package battleaimod.battleai;

import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.Command;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandRunnerControllerTest {
    @After
    public void clearRestartFlag() {
        LudicrousSpeedMod.mustRestart = false;
    }

    @Test
    public void restartStopsRunnerBeforeItConsumesAnotherCommand() {
        AtomicInteger executions = new AtomicInteger();
        Command restart = command("restart", () -> {
            executions.incrementAndGet();
            LudicrousSpeedMod.mustRestart = true;
        });
        Command shouldNotRun = command("next", executions::incrementAndGet);
        CommandRunnerController runner = new CommandRunnerController(
                Arrays.asList(restart, shouldNotRun), true);

        runner.step();
        runner.step();

        assertTrue(runner.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void newRunnerClearsAStaleRestartFlag() {
        LudicrousSpeedMod.mustRestart = true;

        CommandRunnerController runner = new CommandRunnerController(
                Arrays.asList(command("noop", () -> {})), true);

        assertFalse(LudicrousSpeedMod.mustRestart);
        assertFalse(runner.isDone());
    }

    @Test
    public void cancelledRunnerDoesNotExecuteCommands() {
        AtomicInteger executions = new AtomicInteger();
        CommandRunnerController runner = new CommandRunnerController(
                Arrays.asList(command("stale", executions::incrementAndGet)), true);

        runner.cancel();
        runner.step();

        assertTrue(runner.isDone());
        assertEquals(0, executions.get());
    }

    private static Command command(String encoding, Runnable action) {
        return new Command() {
            @Override
            public void execute() {
                action.run();
            }

            @Override
            public String encode() {
                return encoding;
            }
        };
    }
}
