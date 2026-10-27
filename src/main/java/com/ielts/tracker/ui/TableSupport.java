package com.ielts.tracker.ui;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.SimpleStringProperty;

import java.util.function.Function;

final class TableSupport {
    private TableSupport() {}

    static <T> TableColumn<T, String> column(TableView<T> table, String title, double width, Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        c.setPrefWidth(width);
        c.setMinWidth(width);
        table.getColumns().add(c);
        return c;
    }
}
