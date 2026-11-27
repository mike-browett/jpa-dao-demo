package com.aixpertsoft.dcim.api;

import java.util.List;

public record Page<T>(int page, int size, long total, List<T> items) {
}
