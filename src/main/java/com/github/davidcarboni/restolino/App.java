package com.github.davidcarboni.restolino;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.github.davidcarboni.restolino.helpers.Serialiser;

/**
 * This is the framework controller.
 * 
 * @author David Carboni
 * 
 */
public class App extends HttpServlet {

	/**
	 * Generated by Eclipse.
	 */
	// private static final long serialVersionUID = -8124528033609567276L;

	Map<String, RequestHandler> get = new HashMap<String, RequestHandler>();
	Map<String, RequestHandler> put = new HashMap<String, RequestHandler>();
	Map<String, RequestHandler> post = new HashMap<String, RequestHandler>();
	Map<String, RequestHandler> delete = new HashMap<String, RequestHandler>();

	// Map<Class<?>, Map<String, RequestHandler>> methodsa = new
	// HashMap<Class<?>, Map<String, RequestHandler>>();
	// {
	// methodsa.put(GET.class, get);
	// methodsa.put(PUT.class, put);
	// methodsa.put(POST.class, post);
	// methodsa.put(DELETE.class, delete);
	// }
	private Map<String, RequestHandler> getMap(Class<? extends Annotation> type) {
		if (GET.class.isAssignableFrom(type))
			return get;
		else if (PUT.class.isAssignableFrom(type))
			return put;
		else if (POST.class.isAssignableFrom(type))
			return post;
		else if (DELETE.class.isAssignableFrom(type))
			return delete;
		return null;
	}

	private Home home;
	private Boom boom;
	private NotFound notFound;

	@Override
	public void init() throws ServletException {

		// Set up reflections:
		ServletContext servletContext = getServletContext();
		Set<URL> urls = new HashSet<URL>(
				ClasspathHelper.forWebInfLib(servletContext));
		urls.add(ClasspathHelper.forWebInfClasses(servletContext));
		Reflections reflections = new Reflections(
				new ConfigurationBuilder().setUrls(urls));

		configureEndpoints(reflections);
		configureHome(reflections);
		configureNotFound(reflections);
		configureBoom(reflections);
	}

	/**
	 * Searches for and configures all your lovely endpoints.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	@SuppressWarnings("unchecked")
	void configureEndpoints(Reflections reflections) {

		System.out.println("Scanning for endpoints..");
		Set<Class<?>> endpoints = reflections
				.getTypesAnnotatedWith(Endpoint.class);

		System.out.println("Found " + endpoints.size() + " endpoints.");
		System.out.println("Examining endpoint methods..");

		// Configure the classes:
		for (Class<?> endpointClass : endpoints) {
			System.out.println(" - " + endpointClass.getSimpleName());
			for (Method method : endpointClass.getMethods()) {

				// Skip Object methods
				if (method.getDeclaringClass() == Object.class)
					continue;

				// We're looking for public methods that take reqest, responso
				// and optionally a message type:
				Class<?>[] parameterTypes = method.getParameterTypes();
				// System.out.println("Examining method " + method.getName());
				// if (Modifier.isPublic(method.getModifiers()))
				// System.out.println(".public");
				// System.out.println("." + parameterTypes.length +
				// " parameters");
				// if (parameterTypes.length == 2 || parameterTypes.length == 3)
				// {
				// if (HttpServletRequest.class
				// .isAssignableFrom(parameterTypes[0]))
				// System.out.println(".request OK");
				// if (HttpServletResponse.class
				// .isAssignableFrom(parameterTypes[1]))
				// System.out.println(".response OK");
				// }
				if (Modifier.isPublic(method.getModifiers())
						&& parameterTypes.length >= 2
						&& HttpServletRequest.class
								.isAssignableFrom(parameterTypes[0])
						&& HttpServletResponse.class
								.isAssignableFrom(parameterTypes[1])) {

					// Which HTTP method(s) will this method respond to?
					List<Annotation> annotations = Arrays.asList(method
							.getAnnotations());
					// System.out.println("    > processing " +
					// method.getName());
					// for (Annotation annotation : annotations)
					// System.out.println("    >   annotation " +
					// annotation.getClass().getName());
					for (Annotation annotation : annotations) {
						String name = endpointClass.getSimpleName()
								.toLowerCase();

						Map<String, RequestHandler> map = getMap(annotation
								.getClass());
						if (map != null) {
							clashCheck(name, annotation.getClass(),
									endpointClass, method);
							System.out.print("   - "
									+ annotation.getClass().getInterfaces()[0]
											.getSimpleName());
							RequestHandler requestHandler = new RequestHandler();
							requestHandler.endpointClass = endpointClass;
							requestHandler.method = method;
							System.out.print(" " + method.getName());
							if (parameterTypes.length > 2) {
								requestHandler.requestMessageType = parameterTypes[2];
								System.out.print(" request:"
										+ requestHandler.requestMessageType
												.getSimpleName());
							}
							if (method.getReturnType() != void.class) {
								requestHandler.responseMessageType = method
										.getReturnType();
								System.out.print(" response:"
										+ requestHandler.responseMessageType
												.getSimpleName());
							}
							map.put(name, requestHandler);
							System.out.println();
						}
					}
				}
			}
		}

	}

	private void clashCheck(String name,
			Class<? extends Annotation> annotation, Class<?> endpointClass,
			Method method) {
		Map<String, RequestHandler> map = getMap(annotation);
		if (map != null) {
			if (map.containsKey(name))
				System.out.println("   ! method " + method.getName() + " in "
						+ endpointClass.getName() + " overwrites "
						+ map.get(name).method.getName() + " in "
						+ map.get(name).endpointClass.getName() + " for "
						+ annotation.getSimpleName());
		} else {
			System.out.println("WAT. Expected GET/PUT/POST/DELETE but got "
					+ annotation.getName());
		}
	}

	//
	//
	// // Display the signature we've found:
	// System.out.print("   - " + method.getName() + ": ");
	// if (parameterTypes.length > 2)
	// System.out.print("request:"
	// + parameterTypes[2].getSimpleName() + " ");
	// System.out.print("response:"
	// + method.getReturnType().getSimpleName());
	// List<String> httpMethods = new ArrayList<String>();
	// for (Class<?> httpMethod : annotations.size()) {
	//
	// }
	// if (annotations.size() > 0) {
	//
	// StringUtils.join(httpMethods, ",");
	// System.out.println(" ("+httpMethods+")");
	// }

	/**
	 * Searches for and configures the / endpoint.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	@SuppressWarnings("unchecked")
	void configureHome(Reflections reflections) {

		System.out.println("Checking for a / endpoint..");
		home = getEndpoint(Home.class, "/", reflections);
		if (home != null)
			System.out.println("Class " + home.getClass().getSimpleName()
					+ " configured as / endpoint");
	}

	/**
	 * Searches for and configures the not found endpoint.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	@SuppressWarnings("unchecked")
	void configureNotFound(Reflections reflections) {

		System.out.println("Checking for a not-found endpoint..");
		notFound = getEndpoint(NotFound.class, "not-found", reflections);
		if (notFound != null)
			System.out.println("Class " + notFound.getClass().getSimpleName()
					+ " configured as not-found endpoint");
	}

	/**
	 * Searches for and configures the not found endpoint.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	@SuppressWarnings("unchecked")
	void configureBoom(Reflections reflections) {

		System.out.println("Checking for an error endpoint..");
		boom = getEndpoint(Boom.class, "error", reflections);
		if (boom != null)
			System.out.println("Class " + boom.getClass().getSimpleName()
					+ " configured as error endpoint");
	}

	/**
	 * Locates a single endpoint class.
	 * 
	 * @param type
	 * @param name
	 * @param reflections
	 * @return
	 */
	private static <E> E getEndpoint(Class<E> type, String name,
			Reflections reflections) {
		E result = null;

		// Get annotated classes:
		System.out.println("Looking for a " + name + " endpoint..");
		Set<Class<? extends E>> endpointClasses = reflections
				.getSubTypesOf(type);

		if (endpointClasses.size() == 0)

			// No endpoint found:
			System.out.println("No " + name
					+ " endpoint configured. Just letting you know.");

		else {

			// Dump multiple endpoints:
			if (endpointClasses.size() > 1) {
				System.out.println("Warning: found multiple candidates for "
						+ name + " endpoint: " + endpointClasses);
			}

			// Instantiate the endpoint:
			try {
				result = endpointClasses.iterator().next().newInstance();
			} catch (Exception e) {
				System.out.println("Error: cannot instantiate " + name
						+ " endpoint class "
						+ endpointClasses.iterator().next());
				e.printStackTrace();
			}
		}

		return result;
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {

		if (home != null && StringUtils.equals("/", request.getPathInfo())) {
			// Handle a / request:
			Object responseMessage = home.get(request, response);
			if (responseMessage != null)
				writeMessage(response, responseMessage.getClass(),
						responseMessage);
		} else {
			doMethod(request, response, get);
		}
	}

	@Override
	protected void doPut(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
		doMethod(request, response, put);
	}

	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
		doMethod(request, response, post);
	}

	@Override
	protected void doDelete(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
		doMethod(request, response, delete);
	}

	/**
	 * GO!
	 * 
	 * @param request
	 *            The request.
	 * @param response
	 *            The response.
	 * @param requestHandlers
	 *            One of the handler maps.
	 */
	private void doMethod(HttpServletRequest request,
			HttpServletResponse response,
			Map<String, RequestHandler> requestHandlers) {

		// Locate a request handler:
		RequestHandler requestHandler = mapRequestPath(requestHandlers, request);

		try {

			if (requestHandler != null) {

				Object handler = instantiate(requestHandler.endpointClass);
				Object responseMessage = invoke(request, response, handler,
						requestHandler.method,
						requestHandler.requestMessageType);
				if (requestHandler.responseMessageType != null
						&& responseMessage != null) {
					writeMessage(response, requestHandler.responseMessageType,
							responseMessage);
				}

			} else {

				// Not found
				response.setStatus(HttpStatus.SC_NOT_FOUND);
				if (notFound != null)
					notFound.handle(request, response);
			}
		} catch (Throwable t) {

			// Set a default response code:
			response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);

			if (boom != null) {
				try {
					// Attempt to handle the error gracefully:
					boom.handle(request, response, t, requestHandler);
				} catch (Throwable t2) {
					t2.printStackTrace();
				}
			} else {
				t.printStackTrace();
			}
		}

	}

	private static Object invoke(HttpServletRequest request,
			HttpServletResponse response, Object handler, Method method,
			Class<?> requestMessage) {
		Object result = null;

		System.out.println("Invoking method " + method.getName() + " on "
				+ handler.getClass().getSimpleName() + " for request message "
				+ requestMessage);
		try {
			if (requestMessage != null) {
				Object message = readMessage(request, requestMessage);
				result = method.invoke(handler, request, response, message);
			} else {
				result = method.invoke(handler, request, response);
			}
		} catch (Exception e) {
			System.out.println("Error");
			throw new RuntimeException("Error invoking method "
					+ method.getName() + " on "
					+ handler.getClass().getSimpleName());
		}

		System.out.println("Result is " + result);
		return result;
	}

	private static Object readMessage(HttpServletRequest request,
			Class<?> requestMessageType) {

		try (InputStreamReader streamReader = new InputStreamReader(
				request.getInputStream(), "UTF8")) {
			return Serialiser.getBuilder().create()
					.fromJson(streamReader, requestMessageType);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported encoding", e);
		} catch (IOException e) {
			throw new RuntimeException("Error reading message", e);
		}
	}

	private static void writeMessage(HttpServletResponse response,
			Class<?> responseMessageType, Object message) {

		try (OutputStreamWriter writer = new OutputStreamWriter(
				response.getOutputStream(), "UTF8")) {

			Serialiser.getBuilder().create()
					.toJson(message, responseMessageType, writer);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported encoding", e);
		} catch (IOException e) {
			throw new RuntimeException("Error reading message", e);
		}
	}

	/**
	 * Locates a {@link RequestHandler} for the path of the given request.
	 * 
	 * @param requestHandlers
	 *            One of the handler maps.
	 * @param request
	 *            The request.
	 * @return A matching handler, if one exists.
	 */
	private static RequestHandler mapRequestPath(
			Map<String, RequestHandler> requestHandlers,
			HttpServletRequest request) {

		String endpointName = Path.newInstance(request).firstSegment();
		endpointName = StringUtils.lowerCase(endpointName);
		System.out.println("Mapping endpoint " + endpointName);
		return requestHandlers.get(endpointName);
	}

	private static Object instantiate(Class<?> endpointClass) {

		// Instantiate:
		Object result = null;
		try {
			result = endpointClass.newInstance();
		} catch (InstantiationException e) {
			throw new RuntimeException("Unable to instantiate "
					+ endpointClass.getSimpleName(), e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Unable to access "
					+ endpointClass.getSimpleName(), e);
		} catch (NullPointerException e) {
			throw new RuntimeException("No class to instantiate", e);
		}
		return result;

	}

}