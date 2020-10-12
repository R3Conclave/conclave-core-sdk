#pragma once

#include <type_traits>
#include <memory>

#if __cplusplus == 201103L  // Check if we are currently on C++11...
/**
 * Some useful C++>11 (14, 17...) declarations / definitions.
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

template <class T, class... Args>
enable_if_t<!is_array<T>::value, unique_ptr<T>>
make_unique(Args&&... args) {
    return unique_ptr<T>(new T(forward<Args>(args)...));
}

template <typename T>
std::enable_if_t<is_unbounded_array<T>::value, unique_ptr<T>>
make_unique(size_t n) {
    return unique_ptr<T>(new remove_extent_t<T>[n]());
}

template <class T, class... Args>
std::enable_if_t<is_bounded_array<T>::value, std::unique_ptr<T>>
make_unique(Args&&...) = delete;

}  // namespace std

#endif