package eu.siacs.conversations.ui;

import static org.junit.Assert.*;

import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ConversationHistoryTest {
    @Test public void jumpWindowIsBoundedAndRetainsBothEndpointsInEitherDirection() {
        for (int[] pair : new int[][] {{0, 5000}, {5000, 0}, {4999, 5000}, {9999, 4000}}) {
            List<Integer> window = ConversationHistoryKt.navigationWindowIndices(10000, pair[0], pair[1]);
            assertTrue(window.size() <= 31);
            assertTrue(window.contains(pair[0]));
            for (int i = Math.max(0, pair[1] - 10); i <= Math.min(9999, pair[1] + 10); i++) {
                assertTrue(window.contains(i));
            }
        }
    }
    private static class Row extends Message {
        Row(String id, String owner, long time) {
            super((Conversational) null);
            conversationUuid = owner;
            setUuid(id);
            setTime(time);
        }
    }

    @Test public void previousChatRowsNeverReachNewChat() {
        Message mom = new Row("photo", "mom", 1);
        Message alex = new Row("hello", "alex", 2);
        assertEquals(Arrays.asList(alex), ConversationHistoryKt.messagesForConversation(
                "alex", Arrays.asList(mom, alex)));
        assertTrue(ConversationHistoryKt.messagesForConversation(null, Arrays.asList(mom)).isEmpty());
    }

    @Test public void mixedScreenSnapshotCannotPolluteHistoryMerge() {
        Message foreign = new Row("photo", "mom", 1);
        Message pin = new Row("pin", "alex", 2);
        Message recent = new Row("hello", "alex", 100);
        List<Message> merged = ConversationHistoryKt.mergeConversationHistory(
                "alex", Arrays.asList(foreign, recent), Arrays.asList(pin, foreign));
        assertEquals(Arrays.asList(pin, recent), merged);
    }

    @Test public void historyKeepsTimestampSiblingsAndLiveInstancesWithoutDuplicates() {
        Message live = new Row("latest", "alex", 100);
        Message dbCopy = new Row("latest", "alex", 100);
        Message a = new Row("a", "alex", 1);
        Message b = new Row("b", "alex", 1);
        List<Message> merged = ConversationHistoryKt.mergeConversationHistory(
                "alex", Arrays.asList(live), Arrays.asList(a, b, dbCopy, a));
        assertEquals(Arrays.asList(a, b, live), merged);
        assertSame(live, merged.get(2));
    }
}
