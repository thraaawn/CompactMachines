package com.robotgryphon.compactmachines.data.codec;

import com.google.common.collect.ImmutableSet;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class NbtListCollector implements Collector<INBT, List<INBT>, ListNBT> {

    @Override
    public Supplier<List<INBT>> supplier() {
        return ArrayList::new;
    }

    @Override
    public BiConsumer<List<INBT>, INBT> accumulator() {
        return List::add;
    }

    @Override
    public BinaryOperator<List<INBT>> combiner() {
        return (res1, res2) -> {
            res1.addAll(res2);
            return res1;
        };
    }

    @Override
    public Function<List<INBT>, ListNBT> finisher() {
        return (items) -> {
            ListNBT list = new ListNBT();
            list.addAll(items);
            return list;
        };
    }

    @Override
    public Set<Characteristics> characteristics() {
        return ImmutableSet.of(Collector.Characteristics.CONCURRENT,
                Collector.Characteristics.UNORDERED);
    }

    public static NbtListCollector toNbtList() {
        return new NbtListCollector();
    }
}
