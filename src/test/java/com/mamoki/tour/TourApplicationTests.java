package com.mamoki.tour;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 프로파일을 지정하지 않으면 기본 local 로 떠서 개발 스키마(DB_NAME)에 붙는다.
 * 그 프로파일은 ddl-auto: update 라 테스트 한 번이 개발 스키마에 DDL 을 흘려보낸다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TourApplicationTests {

	@Test
	void contextLoads() {
	}

}
