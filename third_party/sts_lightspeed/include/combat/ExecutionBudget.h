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
    const std::chrono::steady_clock::time_point *previous;
    explicit ExecutionBudget(const std::chrono::steady_clock::time_point &deadline):previous(executionDeadline){executionDeadline=&deadline;}
    ~ExecutionBudget(){executionDeadline=previous;}
};
}
