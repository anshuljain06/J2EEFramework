package com.thinking.machines;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.lang.reflect.*;

public class DispatcherServlet extends HttpServlet
{
private MappingHandlerInterface mappingHandler;
private ViewResolverInterface viewResolver;

@SuppressWarnings("deprecation")
public void doGet(HttpServletRequest request, HttpServletResponse response)
{
boolean connectionFlag = false;
Connection connection = null;
boolean isController = false;
boolean isRequestAware = false;
boolean isSessionAware = false;
boolean isBeanAware = false;
boolean isConnectionAware = false;
boolean hasBean = false;
boolean isBean = false;
boolean isValidateAware = false;
Class controllerClass = null;
Controller controller = null;
Class beanClass = null;
Bean bean = null;
Errors errors = null;
String requestName = "";
String viewName = "";
String className = "";
String beanName = "";
String beanScope = "";
try
{
String requestURL = request.getRequestURL().toString();
requestName = getRequestNameFromRequestURL(requestURL);
HttpSession session=request.getSession();
if(session.getAttribute("mappingHandler") == null)
{
populateMappingHandler(session);
}
if(session.getAttribute("viewResolver") == null)
{
populateViewResolver(session);
}
mappingHandler = (MappingHandlerInterface)session.getAttribute("mappingHandler");
viewResolver = (ViewResolverInterface)session.getAttribute("viewResolver");

className = mappingHandler.resolveRequestName(requestName);
if(className == null)
{
System.out.println("No Such Controller Mapping Found : (" + requestName + ")");
response.setStatus(response.SC_INTERNAL_SERVER_ERROR, "No Such Controller Mapping Found : (" + requestName + ")");
return;
}
try
{
controllerClass = Class.forName(className);
}catch(ClassNotFoundException exception)
{
System.out.println("No Such Class Found : (" + className + ")");
response.setStatus(response.SC_INTERNAL_SERVER_ERROR, "No Such Class Found : (" + className + ")");
return;
}

beanName = mappingHandler.getBeanName(requestName);
if(beanName != null)
{
hasBean = true;
try
{
beanClass = Class.forName(beanName);
}catch(ClassNotFoundException exception)
{
System.out.println("No Such Bean Found : (" + beanName + ")");
response.setStatus(response.SC_INTERNAL_SERVER_ERROR, "No Such Bean Found : (" + beanName + ")");
return;
}
Class[] beanInterfaces = beanClass.getInterfaces();
for(int x = 0; x < beanInterfaces.length; x++)
{
switch (beanInterfaces[x].getName())
{
case "com.thinking.machines.Bean":  isBean = true;
         break;
case "com.thinking.machines.ValidateAware":  isValidateAware = true;
         break;
}
System.out.print(beanInterfaces[x].getSimpleName() + " ");
}
if(isBean == false)
{
System.out.println("Specified Bean Class Does Not Implement Bean : (" + beanName + ")");
response.setStatus(response.SC_INTERNAL_SERVER_ERROR, "Specified Bean Class Does Not Implement Bean : (" + beanName + ")");
return;
}
System.out.println("Is Bean");
bean = (Bean)beanClass.newInstance();

populateBeanWithQueryStringData(request, beanClass, bean);

if(isValidateAware)
{
System.out.println("Is Validate Aware");
ValidateAware validateAware = (ValidateAware)bean;
errors = validateAware.validate();
if(errors != null)
{
viewName = viewResolver.resolveLogicalName(errors.getErrorType());
if(viewName == null)
{
response.setStatus(response.SC_INTERNAL_SERVER_ERROR, "No Such View Mapping Found : (" + viewName + ")");
return;
}
HashMap<String,Error> errorHashMap = errors.getErrors();
for(Map.Entry<String, Error> entry : errorHashMap.entrySet())
{
request.setAttribute(entry.getKey(),(Error)entry.getValue());
}
RequestDispatcher requestDispatcher = request.getRequestDispatcher("/" + viewName);
requestDispatcher.forward(request,response);
}
}
beanScope = mappingHandler.getBeanScope(requestName);
switch (beanScope)
{
case "request": request.setAttribute(beanClass.getSimpleName(), bean);
         break;
case "session": session.setAttribute(beanClass.getSimpleName(), bean);
         break;
default: request.setAttribute(beanClass.getSimpleName(), bean);
         break;
}
}

Class[] controllerInterfaces = controllerClass.getInterfaces();
for(int x = 0; x < controllerInterfaces.length; x++)
{
switch (controllerInterfaces[x].getName())
{
case "com.thinking.machines.Controller":  isController = true;
         break;
case "com.thinking.machines.RequestAware":  isRequestAware = true;
         break;
case "com.thinking.machines.SessionAware":  isSessionAware = true;
         break;
case "com.thinking.machines.BeanAware":  isBeanAware = true;
         break;
case "com.thinking.machines.ConnectionAware":  isConnectionAware = true;
         break;
}
System.out.print(controllerInterfaces[x].getSimpleName() + " ");
}

if(isController == false)
{
System.out.println("Specified Class Does Not Implement Controller : (" + className + ")");
response.setStatus(response.SC_INTERNAL_SERVER_ERROR, "Specified Class Does Not Implement Controller : (" + className + ")");
return;
}
System.out.println("Is Controller");
controller = (Controller)controllerClass.newInstance();
if(isRequestAware)
{
System.out.println("Is Request Aware");
RequestAware requestAware = (RequestAware)controller;
requestAware.setRequest(request);
}
if(isSessionAware)
{
System.out.println("Is Session Aware");
SessionAware sessionAware = (SessionAware)controller;
sessionAware.setSession(session);
}
if(isConnectionAware)
{
System.out.println("Is Connection Aware");
ConnectionHandlerInterface connectionHandler = new NewConnectionHandler();
connection = connectionHandler.getConnection(getServletContext().getRealPath("/"));
ConnectionAware connectionAware = (ConnectionAware)controller;
connectionAware.setConnection(connection);
connectionFlag = true;
}
if(isBeanAware && hasBean)
{
System.out.println("Is Bean Aware");
BeanAware beanAware = (BeanAware)controller;
beanAware.setBean(bean);
}
String logicalName = controller.process();
if(connectionFlag)
{
if(!connection.isClosed()) 
{
connection.close();
}
}
viewName = viewResolver.resolveLogicalName(logicalName);
if(viewName == null)
{
response.setStatus(response.SC_INTERNAL_SERVER_ERROR, "No Such View Mapping Found : (" + viewName + ")");
return;
}
RequestDispatcher requestDispatcher = request.getRequestDispatcher("/" + viewName);
requestDispatcher.forward(request,response);
}catch(Exception exception)
{
System.out.println("DispatcherServlet : " + exception);
}
}

public void doPost(HttpServletRequest request, HttpServletResponse response)
{
doGet(request, response);
}

private String getRequestNameFromRequestURL(String requestURL)
{
String requestName = "";
int startingIndex = requestURL.indexOf("/tm/");
int endIndex = requestURL.indexOf("?");
if(endIndex == -1)
{
requestName = requestURL.substring(startingIndex + 4);
}
else
{
requestName = requestURL.substring(startingIndex + 4, endIndex - 1);
}
return requestName;
}

public void populateMappingHandler(HttpSession session)
{
mappingHandler = new NewMappingHandler(getServletContext().getRealPath("/"));
session.setAttribute("mappingHandler",mappingHandler);
}

public void populateViewResolver(HttpSession session)
{
viewResolver = new NewViewResolver(getServletContext().getRealPath("/"));
session.setAttribute("viewResolver",viewResolver);
}

@SuppressWarnings("unchecked")
public void populateBeanWithQueryStringData(HttpServletRequest request, Class beanClass, Bean bean)
{
try
{
Method[] methods = beanClass.getMethods();
Map map = request.getParameterMap();
Set set = map.entrySet();
Iterator iterator = set.iterator();
while(iterator.hasNext())
{
Map.Entry<String,String[]> entry = (Map.Entry<String,String[]>)iterator.next();
String propertyName = entry.getKey();
char[] propertyNameCharArray = propertyName.toCharArray();
propertyNameCharArray[0] = Character.toUpperCase(propertyNameCharArray[0]);
propertyName = String.valueOf(propertyNameCharArray);
String setterName = "set" + propertyName;
for(Method method : methods)
{
if(method.getName().equals(setterName))
{
Type[] types = method.getGenericParameterTypes();
String value = entry.getValue()[0];
if(types.length == 1)
{
String typeName = types[0].toString();
switch(typeName)
{
case "class java.lang.String" : method.invoke(bean, value);
			        break;
case "int" : method.invoke(bean, Integer.parseInt(value));
             break;
case "char": method.invoke(bean, value.toCharArray()[0]);
             break;
case "long": method.invoke(bean, Long.parseLong(value));
             break;
case "byte": method.invoke(bean, Byte.parseByte(value));
             break;
case "short": method.invoke(bean, Short.parseShort(value));
              break;
case "float": method.invoke(bean, Float.valueOf(value));
              break;
case "double": method.invoke(bean, Double.valueOf(value));
               break;
case "class java.util.Date" : method.invoke(bean, new SimpleDateFormat("dd-MM-yyyy").parse(value));
			      break;
case "class java.sql.Date" : java.util.Date date = new SimpleDateFormat("dd-MM-yyyy").parse(value);
			     method.invoke(bean, new java.sql.Date(date.getYear(),date.getMonth(),date.getDate()));
		       	     break;
}
}
}
}
}
}catch(Exception exception)
{
System.out.println("Exception in populateBeanWithQueryStringData : " + exception);
}
}

}
