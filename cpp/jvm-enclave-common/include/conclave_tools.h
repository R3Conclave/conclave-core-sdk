#pragma once

#include <type_traits>
#include <memory>

// Check if we are currently on C++14
#if __cplusplus == 201402L

/**
 * Some useful C++>17/20 definitions.
 */
namespace std {

template <bool B, class T = void>
using enable_if_t = typename enable_if<B, T>::type;

template <typename T>
using remove_extent_t = typename remove_extent<T>::type; 

template <typename>
struct is_unbounded_array : false_type {};

template <typename T>
struct is_unbounded_array<T[]> : true_type {};

template <typename>
struct is_bounded_array : false_type {};

template <typename T, size_t N>
struct is_bounded_array<T[N]> : true_type {};

}  // namespace std

#endif
