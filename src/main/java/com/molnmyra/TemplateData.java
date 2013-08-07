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
				addField(field, data, template, dest, params);
			}
		}
	}

	private static void addField(Field field, Object data, String template, Object dest, Object[] params) {
		//log.debug("Adding field " + field.getName());

		// Try to add by using methods
		for (Method method : data.getClass().getDeclaredMethods()) {
			if ((method.getName().equals(field.getName()) || method.getName().equals("_" + field.getName())) && method.getReturnType().equals(field.getType())) {
				if (method.getParameterTypes().length > 0) {
					Class<?>[] parameterTypes = method.getParameterTypes();
					Object[] invokeParams = new Object[parameterTypes.length];
					boolean methodOk = true;
					outer:
					for (int i = 0; i < parameterTypes.length; i++) {
						Class<?> methodParam = parameterTypes[i];
						for (Object param : params) {
							if (methodParam.isAssignableFrom(param.getClass())) {
								invokeParams[i] = param;
								continue outer;
							}
						}
						if (methodParam.isPrimitive()) {
							methodOk = false;
						} else {
							invokeParams[i] = methodParam.cast(null);
						}
					}

					if (methodOk) {
						try {
							if (!method.isAccessible()) {
								method.setAccessible(true);
							}
							field.set(dest, method.invoke(data, invokeParams));
							return;
						} catch (IllegalAccessException | InvocationTargetException e) {
							log.warn("Could not invoke method " + method.getName(), e);
						}
					}
				}
				if (method.getParameterTypes().length == 0) {
					try {
						//log.debug("Setting field by method invocation");
						if (!method.isAccessible()) {
							method.setAccessible(true);
						}
						field.set(dest, method.invoke(data));
						return;
					} catch (IllegalAccessException | InvocationTargetException e) {
						log.warn("Could not invoke method " + method.getName(), e);
					}
				}
			}
		}

		// Try to add directly if not added before
		try {
			Field dataField = data.getClass().getDeclaredField(field.getName());
			Class<?> fieldType = field.getType().isPrimitive() ? Primitives.wrap(field.getType()) : field.getType();
			Class<?> dataFieldType = dataField.getType().isPrimitive() ? Primitives.wrap(dataField.getType()) : dataField.getType();
			if (fieldType.equals(dataFieldType)) {
				//log.debug("Setting field by direct transfer");
				field.set(dest, dataField.get(data));
				return;
			}
		} catch (NoSuchFieldException e) {
			// No such field, nothing more to do
		} catch (IllegalAccessException e) {
			log.warn("Could not read field " + field.getName() + ". " + e.getMessage());
		}

		// Check if the type is another TemplateEntity
		if (field.getType().getAnnotation(TemplateEntity.class) != null) {
			try {
				Object instance = field.getType().newInstance();
				Field dataField = data.getClass().getDeclaredField(field.getName());
				populate(dataField.get(data), template, instance, params);
				//log.debug("Setting field with another TemplateData");
				field.set(dest, instance);
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (NoSuchFieldException e) {
				// No field available with the name same as the TemplateData object.
			}
		}

		//log.debug("Could not set field");
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

	public static <T> List<T> createList(List<?> data, Class<T> templateDataType, Object... params) {
		return createList(data, null, templateDataType, params);
	}

	public static <T> List<T> createList(List<?> data, String template, Class<T> templateDataType, Object... params) {
		List<T> result = new ArrayList<T>(data.size());
		for (Object obj : data) {
			result.add(create(obj, template, templateDataType, params));
		}
		return result;
	}

	public static <T> T fill(Object data, T destination, Object... params) {
		return fill(data, null, destination, params);
	}

	public static <T> T fill(Object data, String template, T destination, Object... params) {
		populate(data, template, destination, params);
		return destination;
	}
}
