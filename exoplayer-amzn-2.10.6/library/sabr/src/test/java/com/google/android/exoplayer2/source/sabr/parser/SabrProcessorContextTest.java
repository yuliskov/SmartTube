package com.google.android.exoplayer2.source.sabr.parser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextSendingPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextUpdate;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext;
import com.google.protobuf.ByteString;

import org.junit.Test;

public class SabrProcessorContextTest {
    private static final int CONTEXT_TYPE = 42;

    @Test
    public void sendByDefaultFalseDoesNotActivateContext() {
        SabrProcessor processor = createProcessor(null);

        processor.processSabrContextUpdate(contextUpdate(
                CONTEXT_TYPE,
                new byte[] {1},
                false,
                SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_OVERWRITE));

        StreamerContext context = processor.createStreamerContext();
        assertTrue(context.getSabrContextsList().isEmpty());
        assertTrue(context.getUnsentSabrContextsList().isEmpty());
    }

    @Test
    public void stopPolicyDeactivatesActiveContext() {
        SabrProcessor processor = createProcessor(null);
        processor.processSabrContextUpdate(contextUpdate(
                CONTEXT_TYPE,
                new byte[] {1},
                true,
                SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_OVERWRITE));

        processor.processSabrContextSendingPolicy(SabrContextSendingPolicy.newBuilder()
                .addStopPolicy(CONTEXT_TYPE)
                .build());

        StreamerContext context = processor.createStreamerContext();
        assertTrue(context.getSabrContextsList().isEmpty());
        assertTrue(context.getUnsentSabrContextsList().isEmpty());
    }

    @Test
    public void discardPolicyDeletesStoredValueAndDeactivatesContext() {
        SabrProcessor processor = createProcessor(null);
        processor.processSabrContextUpdate(contextUpdate(
                CONTEXT_TYPE,
                new byte[] {1},
                true,
                SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_OVERWRITE));

        processor.processSabrContextSendingPolicy(SabrContextSendingPolicy.newBuilder()
                .addDiscardPolicy(CONTEXT_TYPE)
                .build());
        processor.processSabrContextSendingPolicy(SabrContextSendingPolicy.newBuilder()
                .addStartPolicy(CONTEXT_TYPE)
                .build());

        StreamerContext context = processor.createStreamerContext();
        assertTrue(context.getSabrContextsList().isEmpty());
        assertEquals(1, context.getUnsentSabrContextsCount());
        assertEquals(CONTEXT_TYPE, context.getUnsentSabrContexts(0));
    }

    @Test
    public void keepExistingRetainsValueAndOverwriteReplacesIt() {
        SabrProcessor processor = createProcessor(null);
        processor.processSabrContextUpdate(contextUpdate(
                CONTEXT_TYPE,
                new byte[] {1},
                true,
                SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_OVERWRITE));
        processor.processSabrContextUpdate(contextUpdate(
                CONTEXT_TYPE,
                new byte[] {2},
                true,
                SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_KEEP_EXISTING));

        assertArrayEquals(new byte[] {1}, processor.createStreamerContext()
                .getSabrContexts(0).getValue().toByteArray());

        processor.processSabrContextUpdate(contextUpdate(
                CONTEXT_TYPE,
                new byte[] {3},
                true,
                SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_OVERWRITE));

        assertArrayEquals(new byte[] {3}, processor.createStreamerContext()
                .getSabrContexts(0).getValue().toByteArray());
    }

    @Test
    public void absentPoTokenDoesNotCrashOrEncodeAValue() {
        StreamerContext context = createProcessor(null).createStreamerContext();

        assertFalse(context.hasPoToken());
    }

    private static SabrProcessor createProcessor(String poToken) {
        return new SabrProcessor(
                "",
                StreamerContext.ClientInfo.newBuilder().build(),
                5,
                100,
                0,
                poToken,
                false,
                "video-id",
                60_000);
    }

    private static SabrContextUpdate contextUpdate(
            int type,
            byte[] value,
            boolean sendByDefault,
            SabrContextUpdate.SabrContextWritePolicy writePolicy) {
        return SabrContextUpdate.newBuilder()
                .setType(type)
                .setValue(ByteString.copyFrom(value))
                .setSendByDefault(sendByDefault)
                .setWritePolicy(writePolicy)
                .build();
    }
}
