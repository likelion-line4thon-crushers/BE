package line4thon.boini.presenter.room.repository;

import java.util.Optional;
import line4thon.boini.presenter.room.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
  Optional<Report> findByRoomId(String roomId);
}