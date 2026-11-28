package club.boyuan.official.domain.workbench;

/** 研发工作台在官网侧的固定角色与权限代码。按 role_code 查找，不写死角色 ID。 */
public final class DevRole {
    public static final String CODE = "website_dev_member";
    public static final String ACCESS = "workbench:access";
    public static final String MANAGE = "workbench:member:manage";

    private DevRole() {}
}
