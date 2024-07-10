package org.example;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class NameHierarchy {
    private static class NameElement {
        public String name;
        public String prefix;
        public String postfix;

        public NameElement(String name, String prefix, String postfix) {
            this.name = name;
            this.prefix = prefix;
            this.postfix = postfix;
        }

        public String toJson() {
            return "{" +
                    "\"name\": \"" + this.name + "\"," +
                    "\"prefix\": \"" + this.prefix + "\"," +
                    "\"postfix\": \"" + this.postfix + "\"" +
                   "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NameElement)) return false;

            NameElement that = (NameElement) o;

            return this.name.equals(that.name) && this.prefix.equals(that.prefix) && this.postfix.equals(that.postfix);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, prefix, postfix);
        }
    }

    private final List<NameElement> elements = new LinkedList<>();

    public void appendElement(String name, String prefix, String postfix) {
        elements.add(new NameElement(name, prefix, postfix));
    }

    public void appendElement(String name) {
        appendElement(name, "", "");
    }

    public String toJson() {
        String result = "[";

        boolean first = true;
        for (NameElement element : elements) {
            if (!first) result += ", ";
            first = false;
            result += element.toJson();
        }

        result += "]";
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NameHierarchy)) return false;
        NameHierarchy that = (NameHierarchy) o;

        return this.elements.equals(that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(elements);
    }
}
