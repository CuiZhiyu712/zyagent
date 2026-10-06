package com.zyagent.modules.interview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewTurnStateTest {
    @Test
    void allowsQuestionAnswerEvaluationFollowUpChain() {
        InterviewTurnState state = InterviewTurnState.QUESTION_READY;

        state = state.transitionTo(InterviewTurnState.ANSWERED);
        state = state.transitionTo(InterviewTurnState.EVALUATED);
        state = state.transitionTo(InterviewTurnState.FOLLOW_UP_READY);

        assertEquals(InterviewTurnState.FOLLOW_UP_READY, state);
        assertTrue(state.isTerminal());
    }

    @Test
    void rejectsAnsweringAnAlreadyAnsweredTurn() {
        assertThrows(IllegalStateException.class,
            () -> InterviewTurnState.ANSWERED.transitionTo(InterviewTurnState.ANSWERED));
    }

    @Test
    void rejectsSkippingEvaluation() {
        assertThrows(IllegalStateException.class,
            () -> InterviewTurnState.QUESTION_READY.transitionTo(InterviewTurnState.NEXT_QUESTION_READY));
    }

    @Test
    void terminalTurnStatesCannotAdvance() {
        assertFalse(InterviewTurnState.NEXT_QUESTION_READY.canTransitionTo(InterviewTurnState.ANSWERED));
    }
}
