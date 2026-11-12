package club.boyuan.official.domain.agent.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import club.boyuan.official.integration.agent.AgentAdminClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 周期复盘 / 组织记忆 / 知识库运营(含忠实度抽样)/ 校准案例代理路由的权限边界(同
 * ResumeEvaluationSecurityTest 先例:最小 Spring 上下文只装配控制器与方法级鉴权,
 * 不连数据库,Agent 客户端是替身)。
 *
 * 每条路由对每个权限码各跑一遍,钉住两条契约:
 * - 持有所需权限码之一 → 放行,并且恰好转发一次;
 * - 其余权限码、未登录 → 在 Backend 这一层就被拒,**没有任何请求转发给 Agent**
 *   (不能只靠 Agent 侧拒绝)。
 *
 * 权限码与 Agent 侧一致:复盘的采集/生成/发布、组织记忆的全部接口、判例的
 * 录入/停用/收割要 evaluation:run;复盘读取要 resume:audit 或 evaluation:run;
 * 判例查看要 resume:audit;知识库运营(含忠实度抽样)全部要 kb:manage。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AgentProxySecurityTest.TestConfig.class)
class AgentProxySecurityTest {

    private static final String AUTH = "Bearer user-jwt";

    /** 与这些路由相邻的全部权限码:每条路由都逐个试,防止「给错了码也能进」。 */
    private static final List<String> AUTHORITIES = List.of(
            "agent:monitor", "kb:manage", "resume:audit", "evaluation:run",
            "interview:evaluate", "resume:view");

    private static final Set<String> RUN = Set.of("evaluation:run");
    private static final Set<String> READ = Set.of("resume:audit", "evaluation:run");
    private static final Set<String> AUDIT = Set.of("resume:audit");
    private static final Set<String> KB = Set.of("kb:manage");

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        AgentAdminClient agentAdminClient() {
            // 所有方法都返回 ResponseEntity<String>:放行的调用拿到一个空 200
            return mock(AgentAdminClient.class, invocation -> ResponseEntity.ok("{}"));
        }

        @Bean
        AgentRetrospectiveProxyController retrospectiveController(AgentAdminClient client) {
            return new AgentRetrospectiveProxyController(client, new ObjectMapper());
        }

        @Bean
        AgentKbAdminController kbController(AgentAdminClient client) {
            return new AgentKbAdminController(client, new ObjectMapper());
        }

        @Bean
        AgentEvaluationProxyController evaluationController(AgentAdminClient client) {
            return new AgentEvaluationProxyController(client, new ObjectMapper());
        }

        @Bean
        AgentOrgMemoryProxyController orgMemoryController(AgentAdminClient client) {
            return new AgentOrgMemoryProxyController(client, new ObjectMapper());
        }
    }

    @Autowired
    private AgentAdminClient client;

    @Autowired
    private AgentRetrospectiveProxyController retrospective;

    @Autowired
    private AgentKbAdminController kb;

    @Autowired
    private AgentEvaluationProxyController evaluation;

    @Autowired
    private AgentOrgMemoryProxyController orgMemory;

    @BeforeEach
    void resetClient() {
        clearInvocations(client);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 一条被测路由:名字、放行的权限码、怎么调它。 */
    private record Route(
            String name, Set<String> allowed, Function<AgentProxySecurityTest, ResponseEntity<?>> call) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Map<String, Object> BODY = Map.of("cycle_id", 2026);

    private static final List<Route> ROUTES = List.of(
            // 周期复盘
            new Route("POST retrospective/snapshot", RUN, t -> t.retrospective.takeSnapshot(AUTH, BODY)),
            new Route("GET retrospective/snapshot", READ, t -> t.retrospective.snapshot(AUTH, 2026, null, null)),
            new Route("GET retrospective/snapshots", READ, t -> t.retrospective.snapshots(AUTH, 2026)),
            new Route("POST retrospective/run", RUN, t -> t.retrospective.runReport(AUTH, BODY)),
            new Route("GET retrospective/report", READ, t -> t.retrospective.report(AUTH, 2026, null)),
            new Route("GET retrospective/reports", READ, t -> t.retrospective.reports(AUTH, 2026)),
            new Route("POST retrospective/publish", RUN, t -> t.retrospective.publishReport(AUTH, BODY)),
            // 组织记忆
            new Route("GET org-memory", RUN, t -> t.orgMemory.list(AUTH, null)),
            new Route("POST org-memory/approve", RUN, t -> t.orgMemory.approve(AUTH, BODY)),
            new Route("POST org-memory/retire", RUN, t -> t.orgMemory.retire(AUTH, BODY)),
            new Route("POST org-memory/renew", RUN, t -> t.orgMemory.renew(AUTH, BODY)),
            // 知识库运营
            new Route("POST kb/ops/runs", KB, t -> t.kb.startOpsRun(AUTH, null)),
            new Route("GET kb/ops/runs", KB, t -> t.kb.opsRuns(AUTH, null)),
            new Route("GET kb/ops/topics", KB, t -> t.kb.opsTopics(AUTH, null, null)),
            new Route("GET kb/ops/drafts", KB, t -> t.kb.opsDrafts(AUTH, null, null, null, null)),
            new Route("GET kb/ops/drafts/{id}", KB, t -> t.kb.opsDraftDetail(AUTH, 12L)),
            new Route("PUT kb/ops/drafts/{id}", KB, t -> t.kb.editOpsDraft(AUTH, 12L, BODY)),
            new Route("POST kb/ops/drafts/{id}/approve", KB, t -> t.kb.approveOpsDraft(AUTH, 12L)),
            new Route("POST kb/ops/drafts/{id}/reject", KB, t -> t.kb.rejectOpsDraft(AUTH, 12L)),
            new Route("POST kb/ops/faithfulness/runs", KB, t -> t.kb.startFaithfulnessRun(AUTH, null)),
            new Route("GET kb/ops/faithfulness/runs", KB, t -> t.kb.faithfulnessRuns(AUTH, null)),
            new Route("GET kb/ops/faithfulness/samples", KB, t -> t.kb.faithfulnessSamples(AUTH, null, null)),
            // 校准案例
            new Route("GET evaluation/calibration/cases", AUDIT,
                    t -> t.evaluation.calibrationCases(AUTH, null, null, null, null, null)),
            new Route("POST evaluation/calibration/cases", RUN, t -> t.evaluation.addCalibrationCase(AUTH, BODY)),
            new Route("DELETE evaluation/calibration/cases/{id}", RUN,
                    t -> t.evaluation.retireCalibrationCase(AUTH, 8L)),
            new Route("POST evaluation/calibration/harvest", RUN,
                    t -> t.evaluation.harvestCalibrationCases(AUTH, BODY)));

    static Stream<Arguments> routeAuthorityMatrix() {
        return ROUTES.stream()
                .flatMap(route -> AUTHORITIES.stream().map(authority -> Arguments.of(route, authority)));
    }

    static Stream<Route> routes() {
        return ROUTES.stream();
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("routeAuthorityMatrix")
    void 只有持所需权限码才放行_其余在Backend被拒且不转发(Route route, String authority) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("user", "n/a", authority));

        if (route.allowed().contains(authority)) {
            assertDoesNotThrow(() -> route.call().apply(this), route + " 应对 " + authority + " 放行");
            assertEquals(1, mockingDetails(client).getInvocations().size(),
                    route + " 放行后应恰好转发一次");
        } else {
            assertThrows(AccessDeniedException.class, () -> route.call().apply(this),
                    route + " 应拒绝 " + authority);
            verifyNoInteractions(client);
        }
    }

    @ParameterizedTest(name = "{0} 未登录")
    @MethodSource("routes")
    void 未登录被拒且不转发(Route route) {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> route.call().apply(this),
                route + " 未登录应被拒");
        verifyNoInteractions(client);
    }

    /**
     * 兜底:这几个代理控制器里每个声明了路由的方法都必须带权限(方法上或类上的
     * {@code @PreAuthorize})。以后往里加路由忘了写权限,这条会红。
     */
    @ParameterizedTest
    @ValueSource(classes = {
            AgentRetrospectiveProxyController.class,
            AgentOrgMemoryProxyController.class,
            AgentKbAdminController.class,
            AgentEvaluationProxyController.class})
    void 每个代理路由都声明了权限(Class<?> controller) {
        boolean classLevel = controller.isAnnotationPresent(PreAuthorize.class);
        List<String> unprotected = Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .filter(method -> !classLevel && !method.isAnnotationPresent(PreAuthorize.class))
                .map(Method::getName)
                .toList();

        assertEquals(List.of(), unprotected, controller.getSimpleName() + " 有路由没声明权限");
    }
}
