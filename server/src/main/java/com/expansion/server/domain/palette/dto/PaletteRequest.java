package com.expansion.server.domain.palette.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 팔레트 등록·수정 요청 — 색은 '#rrggbb' 또는 'rrggbb'(서버가 소문자·'#' 붙여 정규화, 중복 제거) */
@Getter
@NoArgsConstructor
public class PaletteRequest {

    @NotBlank(message = "팔레트 이름을 입력해주세요.")
    @Size(max = 50, message = "팔레트 이름은 50자 이하로 입력해주세요.")
    private String name;

    @Size(max = 500, message = "설명은 500자 이하로 입력해주세요.")
    private String description;

    @NotNull(message = "색을 2개 이상 넣어주세요.")
    @Size(min = 2, max = 256, message = "팔레트는 색 2~256개로 만들 수 있습니다.")
    private List<@NotNull @Pattern(regexp = "^#?[0-9a-fA-F]{6}$", message = "색은 #rrggbb 형식이어야 합니다.") String> colors;
}
