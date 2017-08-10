package com.github.sophatvathana.exceller;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import static com.github.sophatvathana.exceller.MapHelper.getRow;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Parser {
	List<InvalidRow> invalidRow;
	
	public Parser() {
		invalidRow = new ArrayList<InvalidRow>();
	}
	
	public <T> List<T> create(Sheet sheet, Class<T> clazz, Consumer<ParserException> errorHandler) {
		List<T> list = new ArrayList<>();
		MapObject excelObject = getObject(clazz, errorHandler);
		if (excelObject.start() <= 0 || excelObject.end() < 0) {
			return list;
		}
		int end = getEnd(sheet, clazz, excelObject);
		
		for (int currentLocation = excelObject.start(); currentLocation <= end; currentLocation++) {
			T object = getNewInstance(sheet, clazz, excelObject.type(), currentLocation, excelObject.zeroIfNull(),
					errorHandler);
			List<Field> mappedExcelFields = getMappedObjects(clazz);
			for (Field mappedField : mappedExcelFields) {
				Class<?> fieldType = mappedField.getType();
				Class<?> clazz1 = fieldType.equals(List.class) ? getFieldType(mappedField) : fieldType;
				List<?> fieldValue = create(sheet, clazz1, errorHandler);
				if (fieldType.equals(List.class)) {
					setFieldValue(mappedField, object, fieldValue);
				} else if (!fieldValue.isEmpty()) {
					setFieldValue(mappedField, object, fieldValue.get(0));
				}
			}
			list.add(object);
		}
		return list;
	}
	
	public <T> List<T> createWithIterator(Sheet sheet, Class<T> clazz, Consumer<ParserException> errorHandler) {
		List<T> list = new ArrayList<T>();
		
		MapObject excelObject = getObject(clazz, errorHandler);
		if (excelObject.start() <= 0 || excelObject.end() < 0) {
			return list;
		}
		int end = getEnd(sheet, clazz, excelObject);
		for (int currentLocation = excelObject.start(); currentLocation <= end; currentLocation++) {
			T object = getNewInstance(sheet.iterator(), sheet.getSheetName(), clazz, excelObject.type(), currentLocation, excelObject.zeroIfNull(),
					errorHandler);
			List<Field> mappedExcelFields = getMappedObjects(clazz);
			for (Field mappedField : mappedExcelFields) {
				Class<?> fieldType = mappedField.getType();
				Class<?> clazz1 = fieldType.equals(List.class) ? getFieldType(mappedField) : fieldType;
				List<?> fieldValue = createWithIterator(sheet, clazz1, errorHandler);
				if (fieldType.equals(List.class)) {
					setFieldValue(mappedField, object, fieldValue);
				} else if (!fieldValue.isEmpty()) {
					setFieldValue(mappedField, object, fieldValue.get(0));
				}
			}
			list.add(object);
		}
		
		return list;
	}
	
	private <T> int getEnd(Sheet sheet, Class<T> clazz, MapObject excelObject) {
		int end = excelObject.end();
		if (end > 0) {
			return end;
		}
		return getRowOrColumnEnd(sheet, clazz);
	}
	
	public <T> List<T> create(Sheet sheet, String sheetName, Class<T> clazz) {
		return create(sheet, clazz, error -> {
			throw error;
		});
	}
	
	public <T> int getRowOrColumnEnd(Sheet sheet, Class<T> clazz) {
		MapObject excelObject = getObject(clazz, e -> {
			throw e;
		});
		Type type = excelObject.type();
		if (type == Type.ROW) {
			return sheet.getLastRowNum() + 1;
		}
		
		Set<Integer> positions = getExcelFieldPositionMap(clazz).keySet();
		OptionalInt maxPosition = positions.stream().mapToInt((x) -> x).max();
		OptionalInt minPosition = positions.stream().mapToInt((x) -> x).min();
		
		int maxCellNumber = 0;
		for (int i = minPosition.getAsInt(); i < maxPosition.getAsInt(); i++) {
			int cellsNumber = sheet.getRow(i).getLastCellNum();
			if (maxCellNumber < cellsNumber) {
				maxCellNumber = cellsNumber;
			}
		}
		return maxCellNumber;
	}
	
	private Class<?> getFieldType(Field field) {
		java.lang.reflect.Type type = field.getGenericType();
		if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			return (Class<?>) pt.getActualTypeArguments()[0];
		}
		
		return null;
	}
	
	private <T> List<Field> getMappedObjects(Class<T> clazz) {
		List<Field> fieldList = new ArrayList<>();
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			MappedObject mappedExcelObject = field.getAnnotation(MappedObject.class);
			if (mappedExcelObject != null) {
				field.setAccessible(true);
				fieldList.add(field);
			}
		}
		return fieldList;
	}
	
	private <T> MapObject getObject(Class<T> clazz, Consumer<ParserException> errorHandler) {
		MapObject excelObject = clazz.getAnnotation(MapObject.class);
		if (excelObject == null) {
			errorHandler.accept(new ParserException("Invalid class configuration - ExcelObject annotation missing - " + clazz.getSimpleName()));
		}
		return excelObject;
	}
	
	private <T> T getNewInstance(Sheet sheet, Class<T> clazz, Type type, Integer currentLocation, boolean zeroIfNull, Consumer<ParserException> errorHandler) {
		T object = getInstance(clazz, errorHandler);
		Map<Integer, Field> excelPositionMap = getExcelFieldPositionMap(clazz);
		for (Integer position : excelPositionMap.keySet()) {
			Field field = excelPositionMap.get(position);
			Object cellValue;
			Object cellValueString;
			if (Type.ROW == type) {
				cellValue = MapHelper.getCellValue(sheet, field.getType(), currentLocation, position, zeroIfNull, errorHandler);
				cellValueString = MapHelper.getCellValue(sheet, String.class, currentLocation, position, zeroIfNull, errorHandler);
			} else {
				cellValue = MapHelper.getCellValue(sheet, field.getType(), position, currentLocation, zeroIfNull, errorHandler);
				cellValueString = MapHelper.getCellValue(sheet, String.class, position, currentLocation, zeroIfNull, errorHandler);
			}
			validateAnnotation(field, cellValueString, position, currentLocation);
			setFieldValue(field, object, cellValue);
		}
		
		return object;
	}
	
	private <T> T getNewInstance(Iterator<Row> rowIterator, String sheetName, Class<T> clazz, Type type, Integer currentLocation, boolean zeroIfNull, Consumer<ParserException> errorHandler) {
		T object = getInstance(clazz, errorHandler);
		Map<Integer, Field> excelPositionMap = getSortedExcelFieldPositionMap(clazz);
		Row row = null;
		for (Integer position : excelPositionMap.keySet()) {
			Field field = excelPositionMap.get(position);
			Object cellValue;
			Object cellValueString;
			
			if (Type.ROW == type) {
				if (null == row || row.getRowNum() + 1 != currentLocation) {
					row = getRow(rowIterator, currentLocation);
				}
				cellValue = MapHelper.getCellValue(row, sheetName, field.getType(), currentLocation, position, zeroIfNull, errorHandler);
				cellValueString = MapHelper.getCellValue(row, sheetName, String.class, currentLocation, position, zeroIfNull, errorHandler);
			} else {
				if (null == row || row.getRowNum() + 1 != position) {
					row = getRow(rowIterator, position);
				}
				cellValue = MapHelper.getCellValue(row, sheetName, field.getType(), position, currentLocation, zeroIfNull, errorHandler);
				cellValueString = MapHelper.getCellValue(row, sheetName, String.class, position, currentLocation, zeroIfNull, errorHandler);
			}
			validateAnnotation(field, cellValueString, position, currentLocation);
			setFieldValue(field, object, cellValue);
		}
		
		return object;
	}
	
	private void validateAnnotation(Field field, Object cellValueString, int position, int currentLocation) {
		MapField annotation = field.getAnnotation(MapField.class);
		if (annotation.validate()) {
			Pattern pattern = Pattern.compile(annotation.regex());
			cellValueString = cellValueString != null ? cellValueString.toString() : "";
			Matcher matcher = pattern.matcher((String) cellValueString);
			if (!matcher.matches()) {
				InvalidRow excelInvalidCell = new InvalidRow(position, currentLocation, (String) cellValueString);
				invalidRow.add(excelInvalidCell);
				if (annotation.validationType() == MapField.ValidationType.HARD) {
					throw new InvalidRowException("Invalid cell value at [" + currentLocation + ", " + position + "] in the sheet. This exception can be suppressed by setting 'validationType' in @ExcelField to 'ValidationType.SOFT");
				}
			}
		}
	}
	
	private <T> T getInstance(Class<T> clazz, Consumer<ParserException> errorHandler) {
		T object;
		try {
			Constructor<T> constructor = clazz.getDeclaredConstructor();
			constructor.setAccessible(true);
			object = constructor.newInstance();
		} catch (Exception e) {
			errorHandler.accept(new ParserException("Exception occurred while instantiating the class " + clazz.getName(), e));
			return null;
		}
		return object;
	}
	
	private <T> void setFieldValue(Field field, T object, Object cellValue) {
		try {
			field.set(object, cellValue);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new ParserException("Exception occurred while setting field value ", e);
		}
	}
	
	private <T> Map<Integer, Field> getExcelFieldPositionMap(Class<T> clazz) {
		Map<Integer, Field> fieldMap = new HashMap<Integer, Field>();
		return fillMap(clazz, fieldMap);
	}
	
	private <T> Map<Integer, Field> getSortedExcelFieldPositionMap(Class<T> clazz) {
		Map<Integer, Field> fieldMap = new TreeMap<Integer, Field>();
		return fillMap(clazz, fieldMap);
	}
	
	private <T> Map<Integer, Field> fillMap(Class<T> clazz, Map<Integer, Field> fieldMap) {
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			MapField excelField = field.getAnnotation(MapField.class);
			if (excelField != null) {
				field.setAccessible(true);
				fieldMap.put(excelField.position(), field);
			}
		}
		return fieldMap;
	}
	
}