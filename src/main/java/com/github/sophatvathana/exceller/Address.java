package com.github.sophatvathana.exceller;

public class Address {
	
	String sheetName;
	int row;
	int col;
	
	public String getSheetName() {
		return sheetName;
	}
	
	public void setSheetName(String sheetName) {
		this.sheetName = sheetName;
	}
	
	public int getRow() {
		return row;
	}
	
	public void setRow(int row) {
		this.row = row;
	}
	
	public int getCol() {
		return col;
	}
	
	public void setCol(int col) {
		this.col = col;
	}
	
	public Address(String sheetName, int row, int col) {
		this.sheetName = sheetName;
		this.row = row;
		this.col = col;
	}
}