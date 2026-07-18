package com.hospitalops.batch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthea CSV(RFC 4180 계열: 따옴표로 감싼 필드 안의 콤마/줄바꿈/이스케이프된 큰따옴표
 * 지원) 파서. 외부 라이브러리(opencsv/commons-csv)를 추가하지 않고 최소 구현으로
 * 처리한다 — Step 1.3의 대상 파일 범위(batch 패키지)만 건드리기 위함.
 */
final class SyntheaCsvReader {

	private SyntheaCsvReader() {
	}

	/** 첫 행을 헤더로 사용해 이후 각 행을 헤더명->값 맵으로 반환한다. */
	static List<Map<String, String>> readAsMaps(Path csvFile) throws IOException {
		List<List<String>> records = readAllRecords(csvFile);
		if (records.isEmpty()) {
			return List.of();
		}
		List<String> header = records.get(0);
		List<Map<String, String>> rows = new ArrayList<>(records.size() - 1);
		for (int r = 1; r < records.size(); r++) {
			List<String> record = records.get(r);
			Map<String, String> row = new LinkedHashMap<>(header.size());
			for (int c = 0; c < header.size(); c++) {
				row.put(header.get(c), c < record.size() ? record.get(c) : "");
			}
			rows.add(row);
		}
		return rows;
	}

	static List<List<String>> readAllRecords(Path csvFile) throws IOException {
		String content = Files.readString(csvFile, StandardCharsets.UTF_8);
		List<List<String>> records = new ArrayList<>();
		List<String> currentRecord = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean inQuotes = false;
		int i = 0;
		int len = content.length();

		while (i < len) {
			char c = content.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < len && content.charAt(i + 1) == '"') {
						field.append('"');
						i += 2;
					} else {
						inQuotes = false;
						i++;
					}
				} else {
					field.append(c);
					i++;
				}
				continue;
			}

			switch (c) {
				case '"' -> {
					inQuotes = true;
					i++;
				}
				case ',' -> {
					currentRecord.add(field.toString());
					field.setLength(0);
					i++;
				}
				case '\r' -> i++;
				case '\n' -> {
					currentRecord.add(field.toString());
					field.setLength(0);
					records.add(currentRecord);
					currentRecord = new ArrayList<>();
					i++;
				}
				default -> {
					field.append(c);
					i++;
				}
			}
		}

		if (field.length() > 0 || !currentRecord.isEmpty()) {
			currentRecord.add(field.toString());
			records.add(currentRecord);
		}
		return records;
	}
}
