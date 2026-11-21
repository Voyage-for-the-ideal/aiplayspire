"""C++ guard lifetime regression, independent of the game and native worker."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipUnless(shutil.which("g++"), "requires a C++17 g++ compiler")
class ExecutionBudgetTests(unittest.TestCase):
    def test_owns_temporary_deadline_and_restores_nested_exception_scope(self):
        root = Path(__file__).resolve().parents[2]
        source = r'''
#include "combat/ExecutionBudget.h"
#include <type_traits>
int main() {
    using namespace sts;
    using Clock = std::chrono::steady_clock;
    static_assert(!std::is_copy_constructible<ExecutionBudget>::value);
    static_assert(!std::is_copy_assignable<ExecutionBudget>::value);
    if (executionDeadline) return 1;
    {
        ExecutionBudget outer(Clock::now() + std::chrono::seconds(30));
        if (executionDeadline != &outer.deadline) return 2;
        checkExecutionBudget();
        try {
            ExecutionBudget inner(Clock::now() - std::chrono::seconds(1));
            if (executionDeadline != &inner.deadline) return 3;
            checkExecutionBudget();
            return 4;
        } catch (const ExecutionTimeout &) {}
        if (executionDeadline != &outer.deadline) return 5;
        checkExecutionBudget();
    }
    return executionDeadline ? 6 : 0;
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "budget.cpp").write_text(source)
            executable = tmp / "budget-test"
            build = subprocess.run([shutil.which("g++"), "-std=c++17", "-O2", "-Wall", "-Wextra", "-Werror",
                                    "-I", str(root / "third_party/sts_lightspeed/include"),
                                    str(tmp / "budget.cpp"), "-o", str(executable)], capture_output=True, text=True, timeout=60)
            self.assertEqual(build.returncode, 0, build.stderr)
            result = subprocess.run([str(executable)], capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
