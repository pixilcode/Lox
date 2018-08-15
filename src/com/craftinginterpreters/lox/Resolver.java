package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

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
import com.craftinginterpreters.lox.Stmt.Return;
import com.craftinginterpreters.lox.Stmt.Throw;
import com.craftinginterpreters.lox.Stmt.Try;
import com.craftinginterpreters.lox.Stmt.Var;
import com.craftinginterpreters.lox.Stmt.While;

/**
 * Resolves and binds variables to </br>
 * avoid problems created by closure </br>
 * </br>
 * Also ensures that <b>return</b> isn't used </br>
 * on the top level
 * 
 * @author dragonfire
 *
 */
public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
	
	private enum ClassType {
		NONE,
		CLASS,
		SUBCLASS
	}
	
	private final Interpreter interpreter;
	private final Stack<Map<String, Boolean>> scopes;
	private FunctionType currentFunction;
	private ClassType currentClass;
	
	Resolver(Interpreter interpreter) {
		
		this.interpreter = interpreter;
		scopes = new Stack<>();
		currentFunction = FunctionType.NONE;
		currentClass = ClassType.NONE;
		
	}
	
	@Override
	public Void visitBlockStmt(Block stmt) {
		
		beginScope();
		resolve(stmt.statements);
		endScope();
		
		return null;
		
	}
	
	@Override
	public Void visitCatchStmt(Catch stmt) {
		
		beginScope();
		
		declare(stmt.identifier);
		define(stmt.identifier);
		
		resolve(stmt.body);
		
		endScope();
		
		return null;
		
	}
	
	@Override
	public Void visitClassStmt(Class stmt) {
		
		ClassType enclosingType = currentClass;
		currentClass = ClassType.CLASS;
		
		declare(stmt.name);
		
		if(stmt.superclass != null) {
			currentClass = ClassType.SUBCLASS;
			resolve(stmt.superclass);
		}
		
		define(stmt.name);
		
		if(stmt.superclass != null) {
			beginScope();
			scopes.peek().put("super", true);
		}
		
		beginScope();
		scopes.peek().put("this", true);
		
		for(Function method : stmt.methods) {
			FunctionType declaration = FunctionType.METHOD;
			
			if(method.name.lexeme.equals("init"))
				declaration = FunctionType.INITIALIZER;
			
			resolveFunction(method, declaration);
		}
		
		if(stmt.superclass != null)
			endScope();
		
		endScope();
		
		currentClass = enclosingType;
		
		return null;
		
	}
	
	@Override
	public Void visitExitStmt(Exit stmt) {
		
		if(stmt.exitCode != null)
			resolve(stmt.exitCode);
		
		return null;
		
	}
	
	@Override
	public Void visitExpressionStmt(Expression stmt) {
		
		resolve(stmt.expression);
		
		return null;
	}
	
	@Override
	public Void visitFunctionStmt(Function stmt) {
		
		declare(stmt.name);
		define(stmt.name);
		
		resolveFunction(stmt, FunctionType.FUNCTION);
		
		return null;
	}
	
	@Override
	public Void visitIfStmt(If stmt) {
		
		resolve(stmt.condition);
		resolve(stmt.thenBranch);
		
		if(stmt.elseBranch != null)
			resolve(stmt.elseBranch);
		
		return null;
	}
	
	@Override
	public Void visitImportStmt(Import stmt) {
		
		resolve(stmt.body);
		
		return null;
		
	}
	
	@Override
	public Void visitIncludeStmt(Include stmt) {
		
		resolve(stmt.body);
		
		return null;
		
	}
	
	@Override
	public Void visitPrintStmt(Print stmt) {
		
		resolve(stmt.expression);
		
		return null;
	}
	
	@Override
	public Void visitReturnStmt(Return stmt) {
		
		if(stmt.value != null) {
			
			if(currentFunction == FunctionType.INITIALIZER)
				Lox.error(stmt.keyword, "Cannot return a value from an initializer");
			
			resolve(stmt.value);
		}
		
		if(currentFunction == FunctionType.NONE)
			Lox.error(stmt.keyword, "Cannot return from top-level code");
		
		return null;
	}
	
	@Override
	public Void visitThrowStmt(Throw stmt) {
		
		resolve(stmt.thrown);
		
		return null;
		
	}
	
	@Override
	public Void visitTryStmt(Try stmt) {
		
		resolve(stmt.body);
		
		for(Stmt.Catch catchStmt : stmt.catches)
			resolve(catchStmt);
		
		if(stmt.finallyStmt != null)
			resolve(stmt.finallyStmt);
		
		return null;
		
	}
	
	@Override
	public Void visitVarStmt(Var stmt) {
		
		declare(stmt.name);
		if(stmt.initializer != null)
			resolve(stmt.initializer);
		define(stmt.name);
		
		return null;
	}
	
	@Override
	public Void visitWhileStmt(While stmt) {
		
		resolve(stmt.condition);
		resolve(stmt.body);
		
		return null;
	}
	
	@Override
	public Void visitAssignExpr(Assign expr) {
		
		resolve(expr.value);
		resolveLocal(expr, expr.name);
		
		return null;
		
	}
	
	@Override
	public Void visitBinaryExpr(Binary expr) {
		
		resolve(expr.left);
		resolve(expr.right);
		
		return null;
	}
	
	@Override
	public Void visitCallExpr(Call expr) {
		
		resolve(expr.callee);
		for(Expr argument : expr.arguments)
			resolve(argument);
		
		return null;
	}
	
	@Override
	public Void visitGetExpr(Get expr) {
		
		resolve(expr.object);
		
		return null;
		
	}
	
	@Override
	public Void visitGroupingExpr(Grouping expr) {
		
		resolve(expr.expression);
		
		return null;
	}
	
	@Override
	public Void visitLambdaExpr(Lambda expr) {
		resolveFunction(expr, FunctionType.FUNCTION);
		return null;
	}
	
	@Override
	public Void visitLiteralExpr(Literal expr) {
		
		return null;
	}
	
	@Override
	public Void visitLogicalExpr(Logical expr) {
		
		resolve(expr.left);
		resolve(expr.right);
		
		return null;
	}
	
	@Override
	public Void visitSetExpr(Set expr) {
		
		resolve(expr.object);
		resolve(expr.value);
		
		return null;
		
	}
	
	@Override
	public Void visitSuperExpr(Super expr) {
		
		if(currentClass == ClassType.NONE)
			Lox.error(expr.keyword, "Cannot use 'super' outside of a class");
		else if(currentClass != ClassType.SUBCLASS)
			Lox.error(expr.keyword, "Cannot use 'super' in a class without a superclass");
		
		resolveLocal(expr, expr.keyword);
		
		return null;
		
	}
	
	@Override
	public Void visitThisExpr(This expr) {
		
		if(currentClass == ClassType.NONE) {
			Lox.error(expr.keyword, "Cannot use 'this' outside of a class");
			return null;
		}
		
		resolveLocal(expr, expr.keyword);
		
		return null;
		
	}
	
	@Override
	public Void visitUnaryExpr(Unary expr) {
		
		resolve(expr.right);
		
		return null;
	}
	
	@Override
	public Void visitVariableExpr(Variable expr) {
		
		if(!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE)
			Lox.error(expr.name, "Cannot read local variable in its own initializer");
		
		resolveLocal(expr, expr.name);
		
		return null;
	}
	
	void resolve(List<Stmt> stmts) {
		
		for(Stmt stmt : stmts)
			resolve(stmt);
	}
	
	private void resolve(Stmt stmt) {
		
		stmt.accept(this);
	}
	
	private void resolve(Expr expr) {
		
		expr.accept(this);
	}
	
	private void resolveLocal(Expr expr, Token name) {
		
		for(int i = scopes.size() - 1; i >= 0; i--) {
			if(scopes.get(i).containsKey(name.lexeme)) {
				interpreter.resolve(expr, (scopes.size() - 1) - i);
				return;
			}
		}
		
		// Not found, assume it's global
		
	}
	
	private void resolveFunction(Function function, FunctionType type) {
		
		FunctionType enclosingFunction = currentFunction;
		currentFunction = type;
		
		beginScope();
		
		for(Token param : function.parameters) {
			declare(param);
			define(param);
		}
		
		resolve(function.body);
		
		endScope();
		
		currentFunction = enclosingFunction;
		
	}
	
	private void resolveFunction(Lambda function, FunctionType type) {
		
		FunctionType enclosingFunction = currentFunction;
		currentFunction = type;
		
		beginScope();
		
		for(Token param : function.parameters) {
			declare(param);
			define(param);
		}
		
		resolve(function.body);
		
		endScope();
		
		currentFunction = enclosingFunction;
		
	}
	
	private void beginScope() {
		
		scopes.push(new HashMap<>());
	}
	
	private void endScope() {
		
		scopes.pop();
	}
	
	private void declare(Token name) {
		
		if(scopes.isEmpty())
			return;
		
		Map<String, Boolean> scope = scopes.peek();
		
		if(scope.containsKey(name.lexeme))
			Lox.error(name, "Variable with this name already declared in this scope");
		
		scope.put(name.lexeme, false);
		
	}
	
	private void define(Token name) {
		
		if(scopes.isEmpty())
			return;
		
		Map<String, Boolean> scope = scopes.peek();
		scope.put(name.lexeme, true);
		
	}
	
}
