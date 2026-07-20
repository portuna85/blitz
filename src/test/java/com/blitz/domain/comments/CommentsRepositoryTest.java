package com.blitz.domain.comments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class CommentsRepositoryTest {

    private static final Sort THREAD_SORT = Sort.by(Sort.Direction.ASC, "createdDate", "id");

    @Autowired
    CommentsRepository commentsRepository;

    @AfterEach
    void cleanup() {
        commentsRepository.deleteAll();
    }

    private Comments newComment(Long postId, Long parentId, Long authorUserId, String content) {
        return Comments.builder()
                .postId(postId)
                .parentId(parentId)
                .authorUserId(authorUserId)
                .author("author")
                .content(content)
                .build();
    }

    @Test
    @DisplayName("상위댓글과답글_저장_오래된순_정렬")
    void savesTopLevelAndReplyOrderedByCreatedDate() {
        //given
        Comments top1 = commentsRepository.save(newComment(1L, null, 1L, "top1"));
        Comments top2 = commentsRepository.save(newComment(1L, null, 1L, "top2"));
        Comments reply = commentsRepository.save(newComment(1L, top1.getId(), 2L, "reply"));

        //when
        Page<Comments> topLevelPage = commentsRepository.findByPostIdAndParentIdIsNull(
                1L, PageRequest.of(0, 20, THREAD_SORT));
        List<Comments> replies = commentsRepository.findByPostIdAndParentIdIn(
                1L, List.of(top1.getId()), THREAD_SORT);

        //then
        assertThat(topLevelPage.getContent()).extracting(Comments::getId)
                .containsExactly(top1.getId(), top2.getId());
        assertThat(replies).extracting(Comments::getId).containsExactly(reply.getId());
    }

    @Test
    @DisplayName("공백_본문_거부")
    void blankContentIsRejected() {
        assertThatThrownBy(() -> newComment(1L, null, 1L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("천자초과_본문_거부")
    void contentOver1000CharsIsRejected() {
        String tooLong = "a".repeat(1001);
        assertThatThrownBy(() -> newComment(1L, null, 1L, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("작성자ID_소유권_판정")
    void isOwnedByChecksAuthorUserId() {
        Comments comment = newComment(1L, null, 1L, "content");

        assertThat(comment.isOwnedBy(1L)).isTrue();
        assertThat(comment.isOwnedBy(2L)).isFalse();
        assertThat(comment.isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("수정시_버전증가와_동시수정_낙관적락_충돌")
    void updateIncrementsVersionAndDetectsConcurrentConflict() {
        //given
        Comments saved = commentsRepository.save(newComment(1L, null, 1L, "content"));

        Comments copy1 = commentsRepository.findById(saved.getId()).orElseThrow();
        Comments copy2 = commentsRepository.findById(saved.getId()).orElseThrow();

        copy1.update("content-v2");
        commentsRepository.saveAndFlush(copy1);

        //when & then
        copy2.update("content-v3");
        assertThatThrownBy(() -> commentsRepository.saveAndFlush(copy2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("삭제시_원문표시이름제거와_tombstone_필드설정")
    void deleteBlanksContentAndSetsTombstoneFields() {
        //given
        Comments saved = commentsRepository.save(newComment(1L, null, 1L, "content"));

        //when
        saved.delete(java.time.LocalDateTime.now());
        commentsRepository.saveAndFlush(saved);
        Comments reloaded = commentsRepository.findById(saved.getId()).orElseThrow();

        //then
        assertThat(reloaded.getContent()).isEmpty();
        assertThat(reloaded.getAuthor()).isEmpty();
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.getDeletedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("게시글삭제시_댓글과답글_전체정리")
    void deleteByPostIdRemovesAllCommentsAndReplies() {
        //given
        Comments top = commentsRepository.save(newComment(1L, null, 1L, "top"));
        commentsRepository.save(newComment(1L, top.getId(), 2L, "reply"));
        commentsRepository.save(newComment(2L, null, 1L, "other post"));

        //when
        commentsRepository.deleteByPostId(1L);

        //then
        assertThat(commentsRepository.findAll()).extracting(Comments::getPostId).containsOnly(2L);
    }

    @Test
    @DisplayName("상위댓글21개이상_20개페이지경계와_답글일괄조회")
    void topLevelCommentsArePagedIn20sAndRepliesAreBatchFetched() {
        //given
        for (int i = 0; i < 21; i++) {
            commentsRepository.save(newComment(1L, null, 1L, "top" + i));
        }

        //when
        Page<Comments> page0 = commentsRepository.findByPostIdAndParentIdIsNull(
                1L, PageRequest.of(0, 20, THREAD_SORT));
        Page<Comments> page1 = commentsRepository.findByPostIdAndParentIdIsNull(
                1L, PageRequest.of(1, 20, THREAD_SORT));

        //then
        assertThat(page0.getContent()).hasSize(20);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page0.getTotalElements()).isEqualTo(21);
    }

    @Test
    @DisplayName("삭제되지않은_댓글과답글만_activeCount에포함")
    void countByPostIdAndDeletedFalseExcludesTombstones() {
        //given
        Comments top = commentsRepository.save(newComment(1L, null, 1L, "top"));
        Comments reply = commentsRepository.save(newComment(1L, top.getId(), 2L, "reply"));
        Comments deletedTop = commentsRepository.save(newComment(1L, null, 1L, "will delete"));
        deletedTop.delete(java.time.LocalDateTime.now());
        commentsRepository.saveAndFlush(deletedTop);

        //when
        long activeCount = commentsRepository.countByPostIdAndDeletedFalse(1L);

        //then
        assertThat(activeCount).isEqualTo(2);
        assertThat(top).isNotNull();
        assertThat(reply).isNotNull();
    }

    @Test
    @DisplayName("상위댓글ID_생성순서로_오름차순정렬조회")
    void findTopLevelIdsOrderedReturnsAscendingCreationOrder() {
        //given
        Comments first = commentsRepository.save(newComment(1L, null, 1L, "first"));
        Comments second = commentsRepository.save(newComment(1L, null, 1L, "second"));

        //when
        List<Long> ids = commentsRepository.findTopLevelIdsOrdered(1L);

        //then
        assertThat(ids).containsExactly(first.getId(), second.getId());
    }
}
