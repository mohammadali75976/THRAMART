package com.company.kiosk;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Toast;

import java.math.BigDecimal;

public class CalculatorActivity extends Activity {
    private EditText expression;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        expression = findViewById(R.id.editExpression);
        expression.setShowSoftInputOnFocus(false);
        Button home = findViewById(R.id.btnCalcHome);
        GridLayout grid = findViewById(R.id.calcGrid);

        home.setOnClickListener(v -> finish());

        String[] labels = new String[] {
                "C", "⌫", "(", ")",
                "7", "8", "9", "÷",
                "4", "5", "6", "×",
                "1", "2", "3", "-",
                "0", ".", "=", "+"
        };

        for (int index = 0; index < labels.length; index++) {
            String label = labels[index];
            Button button = new Button(this);
            button.setText(label);
            button.setTextSize(20f);
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(v -> handleButton(label));

            int row = index / 4;
            int column = index % 4;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(row, 1, 1f),
                    GridLayout.spec(column, 1, 1f)
            );
            params.width = 0;
            params.height = 0;
            params.setMargins(5, 5, 5, 5);
            grid.addView(button, params);
        }
    }

    private void handleButton(String label) {
        String current = expression.getText().toString();
        switch (label) {
            case "C":
                expression.setText("");
                break;
            case "⌫":
                if (!current.isEmpty()) {
                    expression.setText(current.substring(0, current.length() - 1));
                    expression.setSelection(expression.length());
                }
                break;
            case "=":
                calculate(current);
                break;
            default:
                expression.append(label);
                break;
        }
    }

    private void calculate(String input) {
        if (input.trim().isEmpty()) {
            return;
        }
        try {
            String normalized = input.replace('×', '*').replace('÷', '/');
            double result = new ExpressionParser(normalized).parse();
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException("Invalid result");
            }
            String text = BigDecimal.valueOf(result).stripTrailingZeros().toPlainString();
            expression.setText(text);
            expression.setSelection(text.length());
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Calculation check karein", Toast.LENGTH_SHORT).show();
        }
    }

    private static final class ExpressionParser {
        private final String source;
        private int position;

        ExpressionParser(String source) {
            this.source = source;
        }

        double parse() {
            double value = parseExpression();
            skipSpaces();
            if (position != source.length()) {
                throw new IllegalArgumentException("Unexpected character");
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    double divisor = parseFactor();
                    if (divisor == 0d) {
                        throw new ArithmeticException("Division by zero");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipSpaces();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return -parseFactor();
            }
            if (match('(')) {
                double value = parseExpression();
                if (!match(')')) {
                    throw new IllegalArgumentException("Missing parenthesis");
                }
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipSpaces();
            int start = position;
            boolean dotSeen = false;
            while (position < source.length()) {
                char current = source.charAt(position);
                if (Character.isDigit(current)) {
                    position++;
                } else if (current == '.' && !dotSeen) {
                    dotSeen = true;
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new IllegalArgumentException("Number required");
            }
            return Double.parseDouble(source.substring(start, position));
        }

        private boolean match(char expected) {
            skipSpaces();
            if (position < source.length() && source.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }
    }
}
