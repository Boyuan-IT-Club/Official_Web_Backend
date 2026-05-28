package club.boyuan.official.service;

import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;

public interface InterviewFeishuImportService {

    ImportFeishuResponseDTO importSchedules(ImportFeishuRequestDTO request);
}
