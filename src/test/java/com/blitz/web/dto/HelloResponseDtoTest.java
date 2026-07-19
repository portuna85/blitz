package com.blitz.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelloResponseDtoTest {

    @Test
    @DisplayName("레코드_접근자가_값을_반환한다")
    void recordAccessorsReturnValues() {
        //given
        String name = "test";
        int amount = 1000;

        //when
        HelloResponseDto dto = new HelloResponseDto(name, amount);

        //then
        assertThat(dto.name()).isEqualTo(name);
        assertThat(dto.amount()).isEqualTo(amount);
    }
}
