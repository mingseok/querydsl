package study.querydsl.dto;

import lombok.Data;

@Data
public class MemberSearchConddition {

    private String username;
    private String teamName;
    private Integer ageGoe;
    private Integer ageLoe;

}
