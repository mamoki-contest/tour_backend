package com.mamoki.tour.domain.search.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.search.entity.ThemeSynonym;

public interface ThemeSynonymRepository extends JpaRepository<ThemeSynonym, Long> {

    List<ThemeSynonym> findAllByThemeCodeOrderByIdAsc(String themeCode);
}
