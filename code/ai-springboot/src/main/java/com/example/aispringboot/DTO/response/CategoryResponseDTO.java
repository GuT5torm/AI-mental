package com.example.aispringboot.DTO.response;

import lombok.Data;

/**
 * 知识分类。接口名叫 tree，但前端 knowledge.vue 是直接把它当扁平数组用的
 * （{@code data.map(item => ...)}），所以返回扁平列表；parent_id 一并返回，
 * 若后续要做级联选择，前端可自行组装。
 */
@Data
public class CategoryResponseDTO {

    private Long id;
    private Long parentId;
    private String categoryName;
    private String categoryCode;
    private String description;
    private Integer sortOrder;
    private Integer status;
}
