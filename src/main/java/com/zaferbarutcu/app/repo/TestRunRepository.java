package com.zaferbarutcu.app.repo;

import com.zaferbarutcu.app.domain.TestRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {
}
