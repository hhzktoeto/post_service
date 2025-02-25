package faang.school.postservice.dto.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDto {
    private Long id;
    @NotBlank
    private String content;
    private Long authorId;
    private Long projectId;
    private boolean published;
    private boolean deleted;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime publishedAt;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime scheduledAt;
}
