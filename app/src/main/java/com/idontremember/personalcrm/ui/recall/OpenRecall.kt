package com.idontremember.personalcrm.ui.recall

import com.idontremember.personalcrm.data.search.RecallTarget

/** Screens own their routes/sheets; capture identity is dispatched in one place. */
fun RecallTarget.openCapture(onEvent: (Long) -> Unit, onFact: (Long) -> Unit, onFollowUp: (Long) -> Unit) {
    when (this) {
        is RecallTarget.Event -> onEvent(id)
        is RecallTarget.Fact -> onFact(id)
        is RecallTarget.FollowUp -> onFollowUp(id)
        is RecallTarget.Person, is RecallTarget.Tag -> error("Expected a capture target")
    }
}
