package com.mamoki.tour;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.mamoki.tour.global.batch.BatchJobExit;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TourApplication {

	/**
	 * {@code --job=...} 으로 띄웠으면 작업이 끝난 뒤 종료 코드와 함께 내려간다(#95).
	 * 그 외에는 평소대로 서버로 남는다.
	 */
	public static void main(String[] args) {
		BatchJobExit.exitIfBatchJob(SpringApplication.run(TourApplication.class, args),
				System::exit);
	}

}
