package com.molnmyra;

import com.google.common.primitives.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Magnus
 */
public class TemplateData {

	private static Logger log = LoggerFactory.getLogger(TemplateData.class);

	private TemplateData() {
	}

	private static void populate(Object data, String template, Object dest, Object[] params) {
		log.debug("populating {} for template {}", dest.getClass().getName(), template);
		String[] fields = null;
		TemplateEntity closures = dest.getClass().getAnnotation(TemplateEntity.class);
		if (closures != null) {
			Template[] templates = closures.value();
			for (Template templateFields : templates) {
				if (templateFields.name().equalsIgnoreCase(template)) {
					fields = templateFields.fields();
					break;
				}
			}
		}

		if (fields != null) {
			for (String field : fields) {
				try {
					addField(dest.getClass().getDeclaredField(field), data, template, dest, params);
				} catch (NoSuchFieldException e) {
					log.error("The field " + field + " was not declared in the class of type " + data.getClass());
				}
			}
		} else if (template == null) {
			for (Field field : dest.getClass().getDeclaredFields()) {
				addField(field, data, null, dest, params);
			}
		}
	}

	private static void addField(Field field, Object data, String template, Object dest, Object[] params) {
		String propertyName = field.getName();
		Property property = field.getAnnotation(Property.class);
		if (property != null && property.value().length() > 0) {
			propertyName = property.value();
		}

		log.debug("Adding field {}", propertyName);

		// Try to add by using methods
		for (Method method : data.getClass().getDeclaredMethods()) {
			if ((method.getName().equals(propertyName) || method.getName().equals("_" + propertyName))
					&& method.getReturnType().equals(field.getType())) {

				Object[] invokeParameters = null;

				if (method.getParameterTypes().length > 0) {
					invokeParameters = getInvokeParameters(method, 0, params);

					if (invokeParameters == null) { // Not able to use method
						continue;
					}
				}

				log.debug("Found method on source, {}", method.getName());
				setFieldValue(dest, field, data, method, invokeParameters);
				return;
			}
		}

		// Try to add using methods on data object
		for (Method method : dest.getClass().getDeclaredMethods()) {
			if ((method.getName().equals(propertyName) || method.getName().equals("_" + propertyName))
					&& method.getReturnType().equals(field.getType())
					&& method.getParameterTypes().length > 0
					&& method.getParameterTypes()[0].isAssignableFrom(data.getClass())) { // First parameter must be of type data

				Object[] invokeParameters = new Object[1];
				if (method.getParameterTypes().length > 1) {
					invokeParameters = getInvokeParameters(method, 1, params);

					if (invokeParameters == null) { // Not able to use method
						continue;
					}
				}
				invokeParameters[0] = data;

				log.debug("Found method on destination, {}", method.getName());
				setFieldValue(dest, field, dest, method, invokeParameters);
				return;
			}
		}

		// Try to add directly if not added before
		try {
			Field dataField = data.getClass().getDeclaredField(propertyName);
			Class<?> fieldType = field.getType().isPrimitive() ? Primitives.wrap(field.getType()) : field.getType();
			Class<?> dataFieldType = dataField.getType().isPrimitive() ? Primitives.wrap(dataField.getType()) : dataField.getType();
			if (fieldType.equals(dataFieldType)) {
				log.debug("Setting field {} by direct transfer", propertyName);
				field.set(dest, dataField.get(data));
				return;
			}
		} catch (NoSuchFieldException e) {
			// No such field, nothing more to do
		} catch (IllegalAccessException e) {
			log.warn("Could not read field " + propertyName + ". " + e.getMessage());
		}

		// Check if the type is another TemplateEntity
		if (field.getType().getAnnotation(TemplateEntity.class) != null) {
			try {
				Object instance = field.getType().newInstance();
				Field dataField = data.getClass().getDeclaredField(propertyName);
				populate(dataField.get(data), template, instance, params);
				log.debug("Setting field {} with another TemplateData", propertyName);
				field.set(dest, instance);
				return;
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (NoSuchFieldException e) {
				// No field available with the name same as the TemplateData object.
			}
		}

		log.debug("Could not set field");
	}

	private static Object[] getInvokeParameters(Method method, int startIndex, Object[] params) {
		Class<?>[] types = method.getParameterTypes();
		Object[] result = new Object[types.length];

		outer:
		for (int i = startIndex; i < types.length; i++) {
			Class<?> parameterType = types[i];
			for (Object param : params) {
				if (parameterType.isAssignableFrom(param.getClass())) {
					result[i] = param;
					continue outer;
				}
			}

			if (parameterType.isPrimitive()) {
				return null;
			} else {
				result[i] = parameterType.cast(null);
			}
		}

		return result;
	}

	private static void setFieldValue(Object dest, Field field, Object source, Method method, Object[] parameters) {
		try {
			//log.debug("Setting field by method invocation");
			if (!method.isAccessible()) {
				method.setAccessible(true);
			}
			if (parameters != null) {
				field.set(dest, method.invoke(source, parameters));
			} else {
				field.set(dest, method.invoke(source));
			}
		} catch (IllegalAccessException | InvocationTargetException e) {
			log.warn("Could not invoke method " + method.getName(), e);
		}
	}

	public static <T> T create(Object data, Class<T> templateDataType, Object... params) {
		return create(data, null, templateDataType, params);
	}

	public static <T> T create(Object data, String template, Class<T> templateDataType, Object... params) {
		try {
			T templateData = templateDataType.newInstance();
			populate(data, template, templateData, params);
			return templateData;
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unused")
	public static <T> List<T> createList(List<?> data, Class<T> templateDataType, Object... params) {
		return createList(data, null, templateDataType, params);
	}

	public static <T> List<T> createList(List<?> data, String template, Class<T> templateDataType, Object... params) {
		List<T> result = new ArrayList<>(data.size());
		for (Object obj : data) {
			result.add(create(obj, template, templateDataType, params));
		}
		return result;
	}

	@SuppressWarnings("unused")
	public static <T> T fill(Object data, T destination, Object... params) {
		return fill(data, null, destination, params);
	}

	public static <T> T fill(Object data, String template, T destination, Object... params) {
		populate(data, template, destination, params);
		return destination;
	}
}
