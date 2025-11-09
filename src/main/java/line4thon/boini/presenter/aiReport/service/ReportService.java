package line4thon.boini.presenter.aiReport.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import line4thon.boini.presenter.room.entity.Report;
import line4thon.boini.presenter.aiReport.exception.ReportErrorCode;
import line4thon.boini.presenter.room.repository.ReportRepository;
import line4thon.boini.global.common.exception.CustomException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

  private final ReportRepository reportRepository;

  public Report getReportByRoomId(String roomId) {
    return reportRepository.findByRoomId(roomId)
        .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));
  }
}
