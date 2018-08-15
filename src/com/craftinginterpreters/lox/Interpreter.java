package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.craftinginterpreters.lox.Expr.Assign;
import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Call;
import com.craftinginterpreters.lox.Expr.Get;
import com.craftinginterpreters.lox.Expr.Grouping;
import com.craftinginterpreters.lox.Expr.Lambda;
import com.craftinginterpreters.lox.Expr.Literal;
import com.craftinginterpreters.lox.Expr.Logical;
import com.craftinginterpreters.lox.Expr.Set;
import com.craftinginterpreters.lox.Expr.Super;
import com.craftinginterpreters.lox.Expr.This;
import com.craftinginterpreters.lox.Expr.Unary;
import com.craftinginterpreters.lox.Expr.Variable;
import com.craftinginterpreters.lox.RuntimeError.InterpreterRuntimeError;
import com.craftinginterpreters.lox.RuntimeError.UserRuntimeError;
import com.craftinginterpreters.lox.Stmt.Block;
import com.craftinginterpreters.lox.Stmt.Catch;
import com.craftinginterpreters.lox.Stmt.Class;
import com.craftinginterpreters.lox.Stmt.Exit;
import com.craftinginterpreters.lox.Stmt.Expression;
import com.craftinginterpreters.lox.Stmt.Function;
import com.craftinginterpreters.lox.Stmt.If;
import com.craftinginterpreters.lox.Stmt.Import;
import com.craftinginterpreters.lox.Stmt.Include;
import com.craftinginterpreters.lox.Stmt.Print;
import com.craftinginterpreters.lox.Stmt.Throw;
import com.craftinginterpreters.lox.Stmt.Try;
import com.craftinginterpreters.lox.Stmt.Var;
import com.craftinginterpreters.lox.Stmt.While;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

	final Environment globals;
	private Environment environment;
	private int stackSize;
	private final Map<Expr, Integer> locals;
	private final java.util.Scanner in;

	public Interpreter() {

		globals = new Environment();
		environment = globals;
		locals = new HashMap<>();
		stackSize = 0;
		in = new java.util.Scanner(System.in);;
		
		// Define clock() function
		globals.define("clock", new LoxCallable() {

			@Override
			public Object call(Interpreter interpreter, List<Object> arguments) {
				return (double) System.currentTimeMillis() / 1000.0;
			}

			@Override
			public int arity() {
				return 0;
			}

		});
		
		// Define input(String)
		globals.define("input", new LoxCallable() {
			
			@Override
			public Object call(Interpreter interpreter, List<Object> arguments) {
				System.out.print(arguments.get(0));
				return in.nextLine();
			}
			
			@Override
			public int arity() {
				return 1;
			}
			
		});
		
		// Define println(Object)
		globals.define("println", new LoxCallable() {
			
			@Override
			public Object call(Interpreter interpreter, List<Object> arguments) {
				System.out.println(stringify(arguments.get(0)));
				return null;
			}
			
			@Override
			public int arity() {
				return 1;
			}
			
		});
		
		// Define getVar(String)
		globals.define("getVar", new LoxCallable() {

			@Override
			public Object call(Interpreter interpreter, List<Object> arguments) {

				Object var = arguments.get(0);

				if (var instanceof String) {
					Token varToken = new Token("", "", TokenType.IDENTIFIER, (String) var, null, 0);
					return environment.get(varToken);
				}

				return null;

			}

			@Override
			public int arity() {
				return 1;
			}

		});
		
		// Define getProperty(LoxInstance, String)
		globals.define("getProperty", new LoxCallable() {

			@Override
			public Object call(Interpreter interpreter, List<Object> arguments) {

				Object instance = arguments.get(0);
				Object field = arguments.get(1);

				if (!(arguments.get(0) instanceof LoxInstance && arguments.get(1) instanceof String))
					return null;
				
				Token fieldToken = new Token("", "", TokenType.IDENTIFIER, (String) field, null, 0);
				
				return ((LoxInstance) instance).get(fieldToken);

			}

			@Override
			public int arity() {
				return 2;
			}
		});
		
		// Set up for building the 'RuntimeError' class
		
		Map<String, LoxFunction> methods = new HashMap<>();
		
		List<Stmt> body = new ArrayList<>();
		Token name = new Token("", "", TokenType.IDENTIFIER, "message", null, 0);
		
		List<Token> parameters = new ArrayList<>();
		
		body.add(new Stmt.Return(
				new Token("", "", TokenType.RETURN, "return", null, 0),
				new Literal("No message defined")));
		
		Function function = new Function(name, parameters, body);
		
		boolean isInitializer = false;
		
		methods.put("message", new LoxFunction(function, environment, isInitializer));
		
		// Define the 'getType' method
		body = new ArrayList<>(); // Stores the statement for the upcoming "message" function
		name = new Token("", "", TokenType.IDENTIFIER, "getType", null, 0); // Token for the name
		
		parameters = new ArrayList<>(); // No parameters
		
		body.add(new Stmt.Return(
				new Token("", "", TokenType.RETURN, "return", null, 0),
				new Literal("RuntimeError"))); // Simply return the defined message
		
		function = new Function(name, parameters, body); // Create the function to be stored
		
		isInitializer = false; // This is not an initializer
		
		methods.put("getType", new LoxFunction(function, environment, isInitializer));
		
		// Define 'RuntimeError' for try/catch
		globals.define("RuntimeError", new LoxClass("RuntimeError", null, methods));
		
	}

	void interpret(List<Stmt> statements) {

		try {
			for (Stmt statement : statements)
				execute(statement);
		} catch (InterpreterRuntimeError error) {
			Lox.runtimeError(error);
		} catch (UserRuntimeError error) {
			Lox.userError(error);
		} catch (ExitCode exit) {

			if (!(exit.exitCode instanceof Double)) {
				Lox.runtimeError(exit.keyword, "Invalid exit code '" + exit.exitCode + "'");
				return;
			}

			double exitCode = (double) exit.exitCode;

			if (exitCode != (int) exitCode) {
				Lox.runtimeError(exit.keyword, "Exit code must be an integer");
				return;
			}

			System.exit((int) exitCode);

		}
	}

	@Override
	public Void visitBlockStmt(Block stmt) {

		executeBlock(stmt.statements, new Environment(environment));
		return null;
	}
	
	@Override
	public Void visitCatchStmt(Catch stmt) {
		
		execute(stmt.body);
		return null;
		
	}

	@Override
	public Void visitClassStmt(Class stmt) {

		Object superclass = null;

		if (stmt.superclass != null) {
			superclass = evaluate(stmt.superclass);
			if (!(superclass instanceof LoxClass))
				throw new InterpreterRuntimeError(stmt.superclass.name, "Superclass must be a class");
		}

		environment.define(stmt.name.lexeme, null);

		if (stmt.superclass != null) {
			environment = new Environment(environment);
			environment.define("super", superclass);
		}

		Map<String, LoxFunction> methods = new HashMap<>();
		for (Function method : stmt.methods) {
			LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
			methods.put(method.name.lexeme, function);
		}

		LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass) superclass, methods);
		environment.assign(stmt.name, klass);

		if (superclass != null)
			environment = environment.enclosing;

		return null;

	}

	@Override
	public Void visitExitStmt(Exit stmt) {

		if (stmt.exitCode != null)
			throw new ExitCode(stmt.keyword, evaluate(stmt.exitCode));
		else
			throw new ExitCode(stmt.keyword);

	}

	@Override
	public Void visitExpressionStmt(Expression stmt) {

		evaluate(stmt.expression);
		return null;
	}

	@Override
	public Void visitFunctionStmt(Function stmt) {
		
		LoxFunction function = new LoxFunction(stmt, environment, false);
		environment.define(stmt.name.lexeme, function);
		
		return null;

	}

	@Override
	public Void visitIfStmt(If stmt) {

		if (isTruthy(evaluate(stmt.condition)))
			execute(stmt.thenBranch);
		else if (stmt.elseBranch != null)
			execute(stmt.elseBranch);
		return null;
		
	}
	
	@Override
	public Void visitImportStmt(Import stmt) {
		
		executeBlock(stmt.body, environment);
		
		return null;
		
	}
	
	@Override
	public Void visitIncludeStmt(Include stmt) {
		
		executeBlock(stmt.body, environment);
		
		return null;
		
	}

	@Override
	public Void visitPrintStmt(Print stmt) {

		Object value = evaluate(stmt.expression);
		System.out.print(stringify(value));
		return null;
	}

	@Override
	public Void visitReturnStmt(Stmt.Return stmt) {

		Object value = null;

		if (stmt.value != null)
			value = evaluate(stmt.value);

		throw new Return(value);

	}
	
	@Override
	public Void visitThrowStmt(Throw stmt) {
		
		Object thrown = evaluate(stmt.thrown);
		
		// If the object doesn't inherit the 'RuntimeError' class,
		// it can't be thrown
		Token runtimeError = new Token(stmt.keyword.directory, stmt.keyword.file, TokenType.IDENTIFIER, "RuntimeError", null, stmt.keyword.line);
		if(!(thrown instanceof LoxInstance && ((LoxInstance) thrown).klass().inherits(runtimeError)))
			throw new InterpreterRuntimeError(stmt.keyword,
					"Only objects extending 'RuntimeError' can be thrown", false);
		
		// As long as the object extends 'RuntimeError',
		// it will have a message() function
		Token messageToken = new Token(stmt.keyword.directory, stmt.keyword.file, TokenType.IDENTIFIER, "message", null, 0);
		Object message = ((LoxCallable) ((LoxInstance) thrown).get(messageToken)).call(this, new ArrayList<>());
		
		
		throw new UserRuntimeError((LoxInstance) thrown, stringify(message), stmt.keyword);
		
	}
	
	@Override
	public Void visitTryStmt(Try stmt) {
		
		try {
			execute(stmt.body);
		} catch(RuntimeError error) {
			
			if(error instanceof InterpreterRuntimeError && !((InterpreterRuntimeError) error).catchable)
				throw error;
			
			for(Catch catchStmt : stmt.catches) {
				if(errorMatches(catchStmt, error)) {
					
					Environment enclosing = environment;
					environment = new Environment(enclosing);
					
					// Define the error to make it accessible
					if(error instanceof UserRuntimeError)
						environment.define(catchStmt.identifier.lexeme, ((UserRuntimeError) error).instance);
					else { // Define a user-usable version of the InterpreterRuntimeError
						
						// Set up for building the 'InterpreterRuntimeError' class
						
						Map<String, LoxFunction> methods = new HashMap<>(); // Stores the functions of the class
						
						// Define the 'message' method
						List<Stmt> body = new ArrayList<>(); // Stores the statement for the upcoming "message" function
						Token name = new Token("", "", TokenType.IDENTIFIER, "message", null, 0); // Token for the name
						
						List<Token> parameters = new ArrayList<>(); // No parameters
						
						body.add(new Stmt.Return(
								new Token("", "", TokenType.RETURN, "return", null, 0),
								new Literal(error.getMessage()))); // Simply return the defined message
						
						Function function = new Function(name, parameters, body); // Create the function to be stored
						
						boolean isInitializer = false; // This is not an initializer
						
						methods.put("message", new LoxFunction(function, environment, isInitializer));
						
						// Define the 'message' method
						body = new ArrayList<>(); // Stores the statement for the upcoming "message" function
						name = new Token("", "", TokenType.IDENTIFIER, "getType", null, 0); // Token for the name
						
						parameters = new ArrayList<>(); // No parameters
						
						body.add(new Stmt.Return(
								new Token("", "", TokenType.RETURN, "return", null, 0),
								new Literal("InterpreterRuntimeError"))); // Simply return the defined message
						
						function = new Function(name, parameters, body); // Create the function to be stored
						
						isInitializer = false; // This is not an initializer
						
						methods.put("getType", new LoxFunction(function, environment, isInitializer));
						
						Token runtimeError = new Token(catchStmt.identifier.directory, catchStmt.identifier.file, TokenType.IDENTIFIER, "RuntimeError", null, catchStmt.identifier.line);
						
						LoxClass interpreterRuntimeError =  new LoxClass("InterpreterRuntimeError", (LoxClass) globals.get(runtimeError), methods);
						
						// ^ Create an anonymous class that extends RuntimeError
						
						LoxInstance errorInstance = new LoxInstance(interpreterRuntimeError);
						
						/*
						Token name = new Token(catchStmt.identifier.file, TokenType.IDENTIFIER, "message", null, catchStmt.identifier.line);
						
						List<Token> parameters = new ArrayList<>();
						
						List<Stmt> body = new ArrayList<>();
						
						body.add(new Stmt.Return(
								new Token(catchStmt.identifier.file, TokenType.RETURN, "return", null, catchStmt.identifier.line),
								new Literal(error.getMessage())));
						
						Function function = new Function(name, parameters, body);
						
						boolean isInitializer = false;
						
						errorInstance.set(runtimeError, new LoxFunction(function, environment, isInitializer));
						*/
						
						environment.define(catchStmt.identifier.lexeme, errorInstance);
						
					}
					
					execute(catchStmt);
					
					environment = enclosing;
					
				}
			}
			
		} finally {
			if(stmt.finallyStmt != null)
				execute(stmt.finallyStmt);
		}
		
		return null;
		
	}

	@Override
	public Void visitVarStmt(Var stmt) {

		Object value = null;

		if (stmt.initializer != null)
			value = evaluate(stmt.initializer);

		environment.define(stmt.name.lexeme, value);
		return null;

	}

	@Override
	public Void visitWhileStmt(While stmt) {
		
		int stackNum = incrementStack(stmt.keyword);
		
		while (isTruthy(evaluate(stmt.condition))) {
			incrementStack(stmt.keyword);
			execute(stmt.body);
		}
		
		decrementStack(stackNum);

		return null;

	}

	@Override
	public Object visitAssignExpr(Assign expr) {

		Object value = evaluate(expr.value);

		Integer distance = locals.get(expr);

		if (distance != null)
			environment.assignAt(distance, expr.name, value);
		else
			globals.assign(expr.name, value);

		environment.assign(expr.name, value);
		return value;

	}

	@Override
	public Object visitBinaryExpr(Binary expr) {

		Object left = evaluate(expr.left);
		Object right = evaluate(expr.right);

		switch (expr.operator.type) {
		case EQUAL_EQUAL:
			return isEqual(left, right);
		case BANG_EQUAL:
			return !isEqual(left, right);
		case GREATER:
			checkNumberOperand(expr.operator, left, right);
			return (double) left > (double) right;
		case GREATER_EQUAL:
			checkNumberOperand(expr.operator, left, right);
			return (double) left >= (double) right;
		case LESS:
			checkNumberOperand(expr.operator, left, right);
			return (double) left < (double) right;
		case LESS_EQUAL:
			checkNumberOperand(expr.operator, left, right);
			return (double) left <= (double) right;
		case MINUS:
			checkNumberOperand(expr.operator, left, right);
			return (double) left - (double) right;
		case STAR:
			checkNumberOperand(expr.operator, left, right);
			return (double) left * (double) right;
		case SLASH:
			checkNumberOperand(expr.operator, left, right);
			return (double) left / (double) right;
		case PLUS:
			if (left instanceof String || right instanceof String)
				return stringify(left) + stringify(right);
			if (left instanceof Double && right instanceof Double)
				return (double) left + (double) right;
			throw new InterpreterRuntimeError(expr.operator, "Operands must be two numbers or two strings");

		default:
		}

		// Unreachable
		return null;

	}

	@Override
	public Object visitCallExpr(Call expr) {
		
		int stackNum = incrementStack(expr.paren);
		
		Object callee = evaluate(expr.callee);

		List<Object> arguments = new ArrayList<>();
		for (Expr argument : expr.arguments)
			arguments.add(evaluate(argument));

		if (!(callee instanceof LoxCallable))
			throw new InterpreterRuntimeError(expr.paren, "Can only call functions and classes");

		LoxCallable function = (LoxCallable) callee;
		if (arguments.size() != function.arity())
			throw new InterpreterRuntimeError(expr.paren,
					"Expected " + function.arity() + " arguments but got " + arguments.size());
		
		Object result = function.call(this, arguments);
		
		decrementStack(stackNum);

		return result;

	}

	@Override
	public Object visitGetExpr(Get expr) {

		Object object = evaluate(expr.object);

		if (object instanceof LoxInstance)
			return ((LoxInstance) object).get(expr.name);

		throw new InterpreterRuntimeError(expr.name, "Only instances can have properties");

	}

	@Override
	public Object visitGroupingExpr(Grouping expr) {

		return evaluate(expr.expression);
	}
	
	@Override
	public Object visitLambdaExpr(Lambda expr) {
		
		return new LoxFunction(expr, environment, false);
		
	}

	@Override
	public Object visitLiteralExpr(Literal expr) {

		return expr.value;
	}

	@Override
	public Object visitLogicalExpr(Logical expr) {

		Object left = evaluate(expr.left);

		if (expr.operator.type == TokenType.OR  || expr.operator.type == TokenType.PIPE) {
			if (isTruthy(left))
				return left;
		} else {
			if (!isTruthy(left))
				return left;
		}

		return evaluate(expr.right);

	}

	@Override
	public Object visitSetExpr(Set expr) {

		Object object = evaluate(expr.object);

		if (!(object instanceof LoxInstance))
			throw new InterpreterRuntimeError(expr.name, "Only instances have fields");

		Object value = evaluate(expr.value);
		((LoxInstance) object).set(expr.name, value);
		return value;

	}

	@Override
	public Object visitSuperExpr(Super expr) {

		int distance = locals.get(expr);
		LoxClass superclass = (LoxClass) environment.getSuperAt(distance);

		// "this" is always one level nearer than "super"'s environment
		LoxInstance object = (LoxInstance) environment.getThisAt(distance - 1);

		LoxFunction method = superclass.findMethod(object, expr.method.lexeme);

		if (method == null)
			throw new InterpreterRuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'");

		return method;

	}

	@Override
	public Object visitThisExpr(This expr) {

		return lookUpVariable(expr.keyword, expr);
	}

	@Override
	public Object visitUnaryExpr(Unary expr) {

		Object right = evaluate(expr.right);

		switch (expr.operator.type) {
		case BANG:
			return !isTruthy(right);
		case MINUS:
			checkNumberOperand(expr.operator, right);
			return -(double) right;

		default:
		}

		// Unreachable
		return null;

	}

	@Override
	public Object visitVariableExpr(Variable expr) {

		return lookUpVariable(expr.name, expr);
	}

	void resolve(Expr expr, int depth) {

		locals.put(expr, depth);

	}

	private Object lookUpVariable(Token name, Expr expr) {

		Integer distance = locals.get(expr);

		if (distance != null)
			return environment.getAt(distance, name);
		else
			return globals.get(name);

	}

	private Object evaluate(Expr expr) {

		return expr.accept(this);
	}

	private void checkNumberOperand(Token operator, Object operand) {

		if (operand instanceof Double)
			return;
		throw new InterpreterRuntimeError(operator, "Operand must be a number");
	}

	private void checkNumberOperand(Token operator, Object left, Object right) {

		if (left instanceof Double && right instanceof Double)
			return;
		throw new InterpreterRuntimeError(operator, "Operands must be a number");
	}

	private void execute(Stmt statement) {

		statement.accept(this);
	}

	void executeBlock(List<Stmt> statements, Environment environment) {

		Environment previous = this.environment;

		try {

			this.environment = environment;

			for (Stmt statement : statements)
				execute(statement);
			
		} finally {
			this.environment = previous;
		}

	}
	
	private int incrementStack(Token incrementer) {
		
		if(stackSize >= 1024)
			throw new InterpreterRuntimeError(incrementer, "Stack overflow", false);
		
		return stackSize++;
		
	}
	
	private void decrementStack(int stackNum) {
		
		stackSize = stackNum;
	}
	
	private boolean isTruthy(Object object) {

		if (object == null)
			return false;
		if (object instanceof Boolean)
			return (boolean) object;
		if (object instanceof Double)
			return !((double) object == 0);
		
		return true;
		
	}

	private boolean isEqual(Object a, Object b) {

		// nil is only equal to nil
		if (a == null && b == null)
			return true;
		if (a == null)
			return false;
		
		if(a instanceof LoxInstance) {
			Object equals = ((LoxInstance) a).klass().findMethod((LoxInstance) a, "equals");
			if(equals != null &&
				equals instanceof LoxFunction &&
				((LoxFunction) equals).arity() == 1)
				return isTruthy(((LoxFunction) equals).call(this, Arrays.asList(new Object[] {b})));
		}
		
		if(b instanceof LoxInstance) {
			Object equals = ((LoxInstance) b).klass().findMethod((LoxInstance) b, "equals");
			if(equals != null &&
				equals instanceof LoxFunction &&
				((LoxFunction) equals).arity() == 1)
				return isTruthy(((LoxFunction) equals).call(this, Arrays.asList(new Object[] {a})));
		}
		
		return a.equals(b);

	}
	
	private boolean errorMatches(Catch stmt, RuntimeError error) {
		
		for(Token errorType : stmt.errors) {
			
			if(errorType.lexeme.equals("RuntimeError"))
				return true;
			
			if(error instanceof InterpreterRuntimeError && errorType.lexeme.equals("InterpreterRuntimeError"))
				return true;
			
			if(error instanceof InterpreterRuntimeError)
				continue;
			
			if(((UserRuntimeError) error).instance.klass().inherits(errorType))
				return true;
		
		}
		
		return false;
		
	}

	private String stringify(Object object) {

		if (object == null)
			return "nil";

		if (object instanceof Double) {

			String text = object.toString();

			if (text.endsWith(".0"))
				text = text.substring(0, text.length() - 2);

			return text;

		}
		
		if (object instanceof LoxInstance) {
			
			LoxInstance instance = (LoxInstance) object;
			
			Object toString = instance.klass().findMethod(instance, "toString");
			
			if(toString != null
					&& toString instanceof LoxFunction
					&& ((LoxFunction) toString).arity() == 0) {
				Object text = ((LoxFunction) toString).call(this, new ArrayList<>());
				if(text != null)
					return text.toString();
			}
			
		}

		return object.toString();

	}

}
