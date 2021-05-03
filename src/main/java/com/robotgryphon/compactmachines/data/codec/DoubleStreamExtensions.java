package com.robotgryphon.compactmachines.data.codec;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class DoubleStreamExtensions {

    public static final PrimitiveCodec<DoubleStream> CODEC = new PrimitiveCodec<DoubleStream>() {
        @Override
        public <T> DataResult<DoubleStream> read(final DynamicOps<T> ops, final T input) {
            return getDoubleStream(ops, input);
        }

        @Override
        public <T> T write(final DynamicOps<T> ops, final DoubleStream value) {
            return ops.createList(value.mapToObj(ops::createDouble));
        }

        @Override
        public String toString() {
            return "DoubleStream";
        }
    };

    public static DataResult<double[]> fixedDoubleSize(DoubleStream stream, int limit) {
        double[] limited = stream.limit(limit + 1).toArray();
        if (limited.length != limit) {
            String s = "Input is not a list of " + limit + " doubles";
            return limited.length >= limit ? DataResult.error(s, Arrays.copyOf(limited, limit)) : DataResult.error(s);
        } else {
            return DataResult.success(limited);
        }
    }

    public static <T> DataResult<DoubleStream> getDoubleStream(final DynamicOps<T> ops, final T input) {
        return ops.getStream(input).flatMap(stream -> {
            final List<T> list = stream.collect(Collectors.toList());
            if (list.stream().allMatch(element -> ops.getNumberValue(element).result().isPresent())) {
                return DataResult.success(list.stream().mapToDouble(element -> ops.getNumberValue(element).result().get().doubleValue()));
            }
            return DataResult.error("Some elements are not doubles: " + input);
        });
    }
}
