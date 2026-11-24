package com.redloon.prm

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

class HiltTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        val options = arguments ?: Bundle()
        options.putString(
            "listener",
            listOfNotNull(
                options.getString("listener"),
                FailureSummaryListener::class.java.name
            ).joinToString(",")
        )
        super.onCreate(options)
    }

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}

/** Retain failure details in logcat even if the host loses partial instrumentation stdout. */
class FailureSummaryListener : RunListener() {
    private val failures = mutableListOf<Failure>()
    override fun testFailure(failure: Failure) {
        failures += failure
    }
    override fun testRunFinished(result: Result) {
        Log.i("SuiteSummary", "${result.runCount} tests; ${result.failureCount} failures")
        failures.forEach { failure ->
            Log.e("SuiteFailure", "${failure.description}\n${failure.trace.take(6000)}")
        }
    }
}
