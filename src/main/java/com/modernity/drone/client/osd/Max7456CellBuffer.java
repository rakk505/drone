package com.modernity.drone.client.osd;

/** Fixed character buffer matching the analogue MAX7456 OSD grid. */
public final class Max7456CellBuffer {
    public static final int COLUMNS = 30;
    public static final int ROWS = 16;
    public static final char EMPTY = '\0';

    private final char[][] cells = new char[ROWS][COLUMNS];
    private final float[][] offsetX = new float[ROWS][COLUMNS];
    private final float[][] offsetY = new float[ROWS][COLUMNS];

    public void clear() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                cells[row][column] = EMPTY;
                offsetX[row][column] = 0.0F;
                offsetY[row][column] = 0.0F;
            }
        }
    }

    public void write(int column, int row, char value) {
        write(column, row, value, 0.0F, 0.0F);
    }

    public void write(int column, int row, char value, float subCellX, float subCellY) {
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS || value == ' ') {
            return;
        }
        cells[row][column] = value;
        offsetX[row][column] = subCellX;
        offsetY[row][column] = subCellY;
    }

    public void write(float x, float y, String text) {
        if (text == null) {
            return;
        }
        int column = (int) Math.floor(x);
        int row = (int) Math.floor(y);
        float subX = x - column;
        float subY = y - row;
        for (int index = 0; index < text.length(); index++) {
            write(column + index, row, text.charAt(index), subX, subY);
        }
    }

    public void erase(int column, int row, int width, int height) {
        for (int y = row; y < row + height; y++) {
            for (int x = column; x < column + width; x++) {
                write(x, y, EMPTY);
            }
        }
    }

    public char value(int column, int row) {
        return valid(column, row) ? cells[row][column] : EMPTY;
    }

    public float offsetX(int column, int row) {
        return valid(column, row) ? offsetX[row][column] : 0.0F;
    }

    public float offsetY(int column, int row) {
        return valid(column, row) ? offsetY[row][column] : 0.0F;
    }

    public Max7456CellBuffer copy() {
        Max7456CellBuffer result = new Max7456CellBuffer();
        for (int row = 0; row < ROWS; row++) {
            System.arraycopy(cells[row], 0, result.cells[row], 0, COLUMNS);
            System.arraycopy(offsetX[row], 0, result.offsetX[row], 0, COLUMNS);
            System.arraycopy(offsetY[row], 0, result.offsetY[row], 0, COLUMNS);
        }
        return result;
    }

    private static boolean valid(int column, int row) {
        return column >= 0 && column < COLUMNS && row >= 0 && row < ROWS;
    }
}
