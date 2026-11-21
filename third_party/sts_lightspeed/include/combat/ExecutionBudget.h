#pragma once
#include <chrono>
#include <stdexcept>

namespace sts {
struct ExecutionTimeout : std::runtime_error { ExecutionTimeout():std::runtime_error("execution_deadline"){} };
inline thread_local const std::chrono::steady_clock::time_point *executionDeadline=nullptr;
inline void checkExecutionBudget() {
    if(executionDeadline && std::chrono::steady_clock::now()>=*executionDeadline)throw ExecutionTimeout();
}
struct ExecutionBudget {
    // Own the deadline: callers may pass a temporary time_point.
    const std::chrono::steady_clock::time_point deadline;
    const std::chrono::steady_clock::time_point *previous;
    explicit ExecutionBudget(std::chrono::steady_clock::time_point value):deadline(value),previous(executionDeadline){executionDeadline=&deadline;}
    ExecutionBudget(const ExecutionBudget &)=delete;
    ExecutionBudget &operator=(const ExecutionBudget &)=delete;
    ~ExecutionBudget(){executionDeadline=previous;}
};
}
