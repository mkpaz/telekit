package org.telekit.controls.util;

import javafx.beans.WeakListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

public final class BindUtils {

    public static void rebind(BooleanProperty property, ObservableValue<? extends Boolean> observable) {
        property.unbind();
        property.bind(observable);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Boolean bindings                                                      //
    ///////////////////////////////////////////////////////////////////////////

    @SafeVarargs
    public static BooleanBinding and(ObservableValue<Boolean>... properties) {
        return Bindings.createBooleanBinding(
                () -> Arrays.stream(properties).allMatch(ObservableValue::getValue), properties
        );
    }

    @SuppressWarnings("unchecked")
    public static BooleanBinding and(Collection<ObservableValue<Boolean>> properties) {
        return and(properties.toArray(new ObservableValue[0]));
    }

    @SafeVarargs
    public static BooleanBinding or(ObservableValue<Boolean>... values) {
        return Bindings.createBooleanBinding(
                () -> Arrays.stream(values).anyMatch(ObservableValue::getValue), values
        );
    }

    @SuppressWarnings("unchecked")
    public static BooleanBinding or(Collection<ObservableValue<Boolean>> properties) {
        return or(properties.toArray(new ObservableValue[0]));
    }

    public static <E> BooleanBinding ofPredicate(ObservableObjectValue<E> property,
                                                 Predicate<E> predicate) {
        return ofPredicate(property, predicate, null);
    }

    /**
     * Creates boolean binding that verifies that specified observable property
     * matches specified predicate. Optional transformer can be used to transform
     * contained value before checking. Transformer must not return null value.
     */
    public static <E> BooleanBinding ofPredicate(ObservableObjectValue<E> property,
                                                 Predicate<E> predicate,
                                                 Function<E, E> transformer) {
        return Bindings.createBooleanBinding(() -> {

            if (isNull(property)) { return false; }
            E val = transformer == null ? property.get() : transformer.apply(property.get());
            return predicate.test(Objects.requireNonNull(val));
        }, property);
    }

    private static boolean isNull(ObservableObjectValue<?> property) {
        return property == null || property.get() == null;
    }

    private static boolean isNotNull(ObservableObjectValue<?> property) {
        return property != null && property.get() != null;
    }

    public static BooleanBinding isBlank(ObservableObjectValue<String> property) {
        return Bindings.createBooleanBinding(() -> isNull(property) || property.get().isBlank(), property);
    }

    public static BooleanBinding isNotBlank(ObservableObjectValue<String> property) {
        return Bindings.createBooleanBinding(() -> isNotNull(property) && !property.get().isBlank(), property);
    }

    public static BooleanBinding startsWith(ObservableObjectValue<String> value, String prefix) {
        return startsWith(value, prefix, null);
    }

    public static BooleanBinding startsWith(ObservableObjectValue<String> property,
                                            String prefix,
                                            Function<String, String> converter) {
        return ofPredicate(property, val -> prefix != null && val.startsWith(prefix), converter);
    }

    public static BooleanBinding endsWith(ObservableObjectValue<String> property, String suffix) {
        return endsWith(property, suffix, null);
    }

    public static BooleanBinding endsWith(ObservableObjectValue<String> property,
                                          String suffix,
                                          Function<String, String> converter) {
        return ofPredicate(property, val -> suffix != null && val.endsWith(suffix), converter);
    }

    public static <E> BooleanBinding contains(ObservableObjectValue<E> property, Collection<E> c) {
        return contains(property, c, null);
    }

    public static <E> BooleanBinding contains(ObservableObjectValue<E> property,
                                              Collection<E> c,
                                              Function<E, E> converter) {
        return ofPredicate(property, val -> c != null && c.contains(val), converter);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Property bindings                                                       //
    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public static <T> void bindToggleGroup(ToggleGroup toggleGroup, ObjectProperty<T> property) {
        for (Toggle toggle : toggleGroup.getToggles()) {
            if (toggle.getUserData() == null) {
                throw new IllegalArgumentException("The ToggleGroup contains at least one Toggle without user data");
            }

            if (Objects.equals(property.get(), toggle.getUserData())) {
                toggleGroup.selectToggle(toggle);
                break;
            }
        }

        toggleGroup.selectedToggleProperty().addListener((obs, old, value) -> property.set((T) value.getUserData()));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Object bindings                                                       //
    ///////////////////////////////////////////////////////////////////////////

    public static <S, R> ObjectBinding<R> map(ObservableValue<S> source,
                                              Function<? super S, ? extends R> function,
                                              R defaultValue) {
        return Bindings.createObjectBinding(() -> {
            S sourceValue = source.getValue();

            if (sourceValue == null) {
                return defaultValue;
            } else {
                return function.apply(sourceValue);
            }
        }, source);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Content mapping                                                       //
    ///////////////////////////////////////////////////////////////////////////

    public static <E, F> void mapContent(ObservableList<F> mapped,
                                         ObservableList<? extends E> source,
                                         Function<? super E, ? extends F> mapper) {
        map(mapped, source, mapper);
    }

    private static <E, F> void map(ObservableList<F> mapped,
                                   ObservableList<? extends E> source,
                                   Function<? super E, ? extends F> mapper) {
        final ListContentMapping<E, F> contentMapping = new ListContentMapping<>(mapped, mapper);
        mapped.setAll(source.stream().map(mapper).collect(toList()));
        source.removeListener(contentMapping);
        source.addListener(contentMapping);
    }

    private static class ListContentMapping<E, F> implements ListChangeListener<E>, WeakListener {

        private final WeakReference<List<F>> mappedRef;
        private final Function<? super E, ? extends F> mapper;

        public ListContentMapping(List<F> mapped, Function<? super E, ? extends F> mapper) {
            this.mappedRef = new WeakReference<>(mapped);
            this.mapper = mapper;
        }

        @Override
        public void onChanged(Change<? extends E> change) {
            final List<F> mapped = mappedRef.get();
            if (mapped == null) {
                change.getList().removeListener(this);
            } else {
                while (change.next()) {
                    if (change.wasPermutated()) {
                        mapped.subList(change.getFrom(), change.getTo()).clear();
                        mapped.addAll(change.getFrom(), change.getList().subList(change.getFrom(), change.getTo())
                                .stream().map(mapper).collect(toList()));
                    } else {
                        if (change.wasRemoved()) {
                            mapped.subList(change.getFrom(), change.getFrom() + change.getRemovedSize()).clear();
                        }
                        if (change.wasAdded()) {
                            mapped.addAll(change.getFrom(), change.getAddedSubList()
                                    .stream().map(mapper).collect(toList()));
                        }
                    }
                }
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return mappedRef.get() == null;
        }

        @Override
        public int hashCode() {
            final List<F> list = mappedRef.get();
            return (list == null) ? 0 : list.hashCode();
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }

            final List<F> mapped1 = mappedRef.get();
            if (mapped1 == null) {
                return false;
            }

            if (that instanceof ListContentMapping<?, ?> other) {
                final List<?> mapped2 = other.mappedRef.get();
                return mapped1 == mapped2;
            }
            return false;
        }
    }
}
