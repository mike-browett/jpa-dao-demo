package com.aixpertsoft.datastack.api;

import java.util.List;

/** One page of a list: what every list endpoint and view returns. */
public record Page<T>(int page, int size, Long total, List<T> items) {
}
