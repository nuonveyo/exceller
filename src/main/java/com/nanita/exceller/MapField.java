package com.nanita.exceller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface MapField {
	int position();
	enum ValidationType
	{
		SOFT, HARD
	}
	ValidationType validationType() default  ValidationType.SOFT;
	boolean validate() default false;
	String  regex() default ".*";
}