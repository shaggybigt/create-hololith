package dev.hololith.survey;

import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurveyFollowerTest {
    private static final SurveyKey A=SurveyKey.at("minecraft:overworld",0,0,32);
    private static final SurveyKey B=SurveyKey.at("minecraft:overworld",8,0,32);
    private static final SurveyKey C=SurveyKey.at("minecraft:overworld",16,0,32);
    private static SurveySnapshot snapshot(SurveyKey key){
        int n=key.size()*key.size();byte[] flags=new byte[n];Arrays.fill(flags,(byte)1);
        return new SurveySnapshot(key,1000,1,new int[n],new int[n],new int[n],new int[n],
            new BlockState[n],new BlockState[n],flags,n);
    }
    private static final class Queue implements SurveyFollower.Source {
        final Map<SurveyKey,SurveySnapshot> cache=new HashMap<>();
        final Set<SurveyKey> pending=new HashSet<>(),checked=new HashSet<>();
        final List<SurveyKey> requests=new ArrayList<>();
        public SurveySnapshot cached(SurveyKey key){return cache.get(key);}
        public SurveySnapshot request(SurveyKey key){
            requests.add(key);if(!checked.contains(key))pending.add(key);return cached(key);
        }
        public boolean isPending(SurveyKey key){return pending.contains(key);}
        void complete(SurveyKey key,boolean terrain){pending.remove(key);checked.add(key);if(terrain)cache.put(key,snapshot(key));}
    }
    @Test void completedOldRegionHandsOffToCurrentTargetInTheSamePoll(){
        var queue=new Queue();var follower=new SurveyFollower();
        assertTrue(follower.poll(A,queue).pending());queue.complete(A,true);queue.requests.clear();
        var update=follower.poll(B,queue);
        assertEquals(List.of(B),queue.requests);assertSame(queue.cache.get(A),update.snapshot());
        assertEquals(Set.of(B),queue.pending);assertTrue(update.pending());
    }
    @Test void fastMotionCompletesOneUsefulRegionInsteadOfQueuingEveryIntermediatePosition(){
        var queue=new Queue();var follower=new SurveyFollower();follower.poll(A,queue);
        follower.poll(B,queue);follower.poll(C,queue);
        assertEquals(Set.of(A),queue.pending);assertFalse(queue.requests.contains(B));assertFalse(queue.requests.contains(C));
        queue.complete(A,true);follower.poll(C,queue);
        assertEquals(Set.of(C),queue.pending);assertFalse(queue.requests.contains(B));
    }
    @Test void cachedCurrentLocationCanDisplayWhileOutstandingWorkFinishes(){
        var queue=new Queue();var follower=new SurveyFollower();follower.poll(A,queue);
        queue.cache.put(B,snapshot(B));
        assertSame(queue.cache.get(B),follower.poll(B,queue).snapshot());assertEquals(Set.of(A),queue.pending);
        queue.complete(A,true);assertSame(queue.cache.get(B),follower.poll(B,queue).snapshot());
    }
    @Test void emptyOrEvictedCompletionStillAllowsLatestTargetToStart(){
        var queue=new Queue();var follower=new SurveyFollower();follower.poll(A,queue);
        queue.complete(A,false);queue.requests.clear();
        var update=follower.poll(B,queue);
        assertNull(update.snapshot());assertEquals(List.of(B),queue.requests);assertTrue(update.pending());
    }
    @Test void unfinishedOldRefreshDoesNotRollBackARecentlyDisplayedCache(){
        var queue=new Queue();var follower=new SurveyFollower();queue.cache.put(A,snapshot(A));queue.cache.put(B,snapshot(B));
        assertSame(queue.cache.get(A),follower.poll(A,queue).snapshot());
        assertSame(queue.cache.get(B),follower.poll(B,queue).snapshot());
        assertNull(follower.poll(C,queue).snapshot());assertEquals(Set.of(A),queue.pending);
    }
    @Test void completedStationaryScanCanRespectManagersRefreshDelay(){
        var queue=new Queue();var follower=new SurveyFollower();follower.poll(A,queue);queue.complete(A,true);
        assertFalse(follower.poll(A,queue).pending());
        assertSame(queue.cache.get(A),follower.poll(A,queue).snapshot());assertTrue(queue.pending.isEmpty());
    }
    @Test void explicitRetargetStopsKeepingPreviousRequestAlive(){
        var queue=new Queue();var follower=new SurveyFollower();follower.poll(A,queue);follower.reset();queue.requests.clear();
        follower.poll(B,queue);assertEquals(List.of(B),queue.requests);
    }
}
