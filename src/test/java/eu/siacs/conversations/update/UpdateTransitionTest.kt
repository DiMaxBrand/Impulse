package eu.siacs.conversations.update

import org.junit.Assert.*
import org.junit.Test

class UpdateTransitionTest {
    @Test fun graduallyMovesOnlyToMoreStableInstalledBuilds() {
        var state = ChannelTransition.choose(UpdateChannel.ALPHA, UpdateChannel.STABLE, true, UpdateChannel.ALPHA)
        assertEquals(UpdateChannel.ALPHA to UpdateChannel.STABLE, state)
        state = ChannelTransition.advance(state.first, state.second!!, UpdateChannel.BETA)
        assertEquals(UpdateChannel.BETA to UpdateChannel.STABLE, state)
        state = ChannelTransition.advance(state.first, state.second!!, UpdateChannel.ALPHA)
        assertEquals(UpdateChannel.BETA to UpdateChannel.STABLE, state)
        state = ChannelTransition.advance(state.first, state.second!!, UpdateChannel.RC)
        assertEquals(UpdateChannel.RC to UpdateChannel.STABLE, state)
        state = ChannelTransition.advance(state.first, state.second!!, UpdateChannel.STABLE)
        assertEquals(UpdateChannel.STABLE to null, state)
    }
    @Test fun installedTargetSwitchesImmediately() {
        assertEquals(UpdateChannel.BETA to null, ChannelTransition.choose(UpdateChannel.ALPHA, UpdateChannel.BETA, true, UpdateChannel.RC))
    }
}
