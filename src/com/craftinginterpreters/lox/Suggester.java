package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Comparator;
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
 * <p>
 * Gives suggestions and warnings where code could be improved
 * </p>
 * 
 * <p>
 * Current tasks:
 * </p>
 * <ul>
 * <li>Unused local variables</li>
 * <li>Detect when this.{variable} overrides a function declared in the class</li>
 * <li>Code after return, exit statements</li>
 * </ul>
 * 
 * <p>
 * Future tasks:
 * </p>
 * <ul>
 * <li>Code after break, continue statements</li>
 * <li>Detect variables declared in other files that are also declared in user code</li>
 * <li>Detect when variable declarations overwrite parameters</li>
 * </ul>
 *
 */
public class Suggester implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
	
	private final List<Warning> warnings;
	
	private final Stack<Map<Token, Boolean>> varsUsed;
	private final Stack<List<Token>> declared;
	
	private Token currentClass;
	private Map<Token, Token> inheritance;
	private Map<Token, List<Token>> classMethods;
	private Map<Token, List<Token>> classVariables;
	
	private final ControlFlowTracker returned;
	private final ControlFlowTracker exited;
	private final ControlFlowTracker thrown;
	
	Suggester() {
		
		warnings = new ArrayList<>();
		
		varsUsed = new Stack<>();
		declared = new Stack<>();
		
		declared.push(new ArrayList<>());
		
		currentClass = null;
		inheritance = new HashMap<>();
		classMethods = new HashMap<>();
		classVariables = new HashMap<>();
		
		returned = new ControlFlowTracker();
		exited = new ControlFlowTracker();
		thrown = new ControlFlowTracker();
		
	}
	
	public void suggest(List<Stmt> statements) {
		
		exited.initialize();
		thrown.initialize();
		check(statements);
		exited.end();
		thrown.end();
		
		checkClassOverwrite();
		
		warnings.sort(new Comparator<Warning>() {

			@Override
			public int compare(Warning o1, Warning o2) {
				return ((Integer) o1.token.line).compareTo((Integer) o2.token.line);
			}
			
		});
		
		for(Warning warning : warnings)
			if(warning.useLine)
				Lox.warning(warning.token.file, warning.token.line, warning.message);
			else
				Lox.warning(warning.token, warning.message);
		
	}
	
	@Override
	public Void visitBlockStmt(Block stmt) {
		
		beginScope();
		check(stmt.statements);
		endScope();
		
		return null;
		
	}
	
	@Override
	public Void visitCatchStmt(Catch stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.identifier, "Unreachable code", true);
			return null;
		}
		
		beginScope();
		check(stmt.body);
		endScope();
		
		return null;
		
	}
	
	@Override
	public Void visitClassStmt(Class stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.name, "Unreachable code", true);
			return null;
		}
		
		if(stmt.superclass != null) {
			check(stmt.superclass);
			inheritance.put(stmt.name, stmt.superclass.name);
		} else {
			inheritance.put(stmt.name, null);
		}
		
		define(stmt.name);
		currentClass = stmt.name;
		
		classMethods.put(stmt.name, new ArrayList<>());
		classVariables.put(stmt.name, new ArrayList<>());
		
		if(stmt.superclass != null)
			beginScope();
		
		beginScope();
		
		for(Function method : stmt.methods)
			checkFunction(method, FunctionType.METHOD);
		
		endScope();
		
		if(stmt.superclass != null)
			endScope();
		
		currentClass = null;
		
		return null;
		
	}
	
	@Override
	public Void visitExitStmt(Exit stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		if(stmt.exitCode != null)
			check(stmt.exitCode);
		
		exited.markAsSeen();
		
		return null;
		
	}
	
	@Override
	public Void visitExpressionStmt(Expression stmt) {
		
		check(stmt.expression);
		
		return null;
		
	}
	
	@Override
	public Void visitFunctionStmt(Function stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.name, "Unreachable code", true);
			return null;
		}
		
		returned.initialize();
		exited.startLevel();
		thrown.startLevel();
		
		define(stmt.name);
		checkFunction(stmt, FunctionType.FUNCTION);
		
		returned.end();
		exited.endLevel();
		thrown.endLevel();
		
		return null;
		
	}
	
	@Override
	public Void visitIfStmt(If stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code");
			return null;
		}
		
		returned.startLevel();
		exited.startLevel();
		thrown.startLevel();
		
		check(stmt.condition);
		check(stmt.thenBranch);
		
		returned.endLevel();
		exited.endLevel();
		thrown.endLevel();
		
		if(stmt.elseBranch != null) {
			returned.startLevel();
			check(stmt.elseBranch);
			returned.endLevel();
		}
		
		return null;
		
	}
	
	@Override
	public Void visitImportStmt(Import stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		exited.initialize();
		thrown.initialize();
		check(stmt.body);
		exited.end();
		thrown.end();
		
		return null;
		
	}
	
	@Override
	public Void visitIncludeStmt(Include stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		exited.initialize();
		thrown.initialize();
		check(stmt.body);
		exited.end();
		thrown.end();
		
		return null;
		
	}
	
	@Override
	public Void visitPrintStmt(Print stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		check(stmt.expression);
		
		return null;
		
	}
	
	@Override
	public Void visitReturnStmt(Return stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		if(stmt.value != null)
			check(stmt.value);
		
		returned.markAsSeen();
		
		return null;
		
	}
	
	@Override
	public Void visitThrowStmt(Throw stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		check(stmt.thrown);
		
		thrown.markAsSeen();
		
		return null;
		
	}
	
	@Override
	public Void visitTryStmt(Try stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		boolean errorTreated = false;
		
		returned.startLevel();
		exited.startLevel();
		thrown.startLevel();
		
		check(stmt.body);
		
		returned.endLevel();
		exited.endLevel();
		thrown.endLevel();
		
		if(stmt.catches.size() > 0)
			errorTreated = true;
		
		for(Stmt.Catch catchStmt : stmt.catches) {
			returned.startLevel();
			exited.startLevel();
			thrown.startLevel();
			
			check(catchStmt);
			
			returned.endLevel();
			exited.endLevel();
			thrown.endLevel();
		}
		
		if(stmt.finallyStmt != null) {
			check(stmt.finallyStmt);
			errorTreated = true;
		}
		
		if(!errorTreated)
			addWarning(stmt.keyword, "'try' statement does not treat any errors");
		
		return null;
		
	}
	
	@Override
	public Void visitVarStmt(Var stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.name, "Unreachable code", true);
			return null;
		}
		
		if(stmt.initializer != null)
			check(stmt.initializer);
		
		define(stmt.name);
		
		return null;
	}
	
	@Override
	public Void visitWhileStmt(While stmt) {
		
		if(isUnreachable()) {
			addWarning(stmt.keyword, "Unreachable code", true);
			return null;
		}
		
		returned.startLevel();
		exited.startLevel();
		thrown.startLevel();
		
		check(stmt.condition);
		check(stmt.body);
		
		returned.endLevel();
		exited.endLevel();
		thrown.endLevel();
		
		return null;
		
	}
	
	@Override
	public Void visitAssignExpr(Assign expr) {
		
		if(isUnreachable()) {
			addWarning(expr.name, "Unreachable code", true);
			return null;
		}
		
		check(expr.value);
		
		return null;
	}
	
	@Override
	public Void visitBinaryExpr(Binary expr) {
		
		if(isUnreachable()) {
			addWarning(expr.operator, "Unreachable code", true);
			return null;
		}
		
		check(expr.left);
		check(expr.right);
		
		return null;
		
	}
	
	@Override
	public Void visitCallExpr(Call expr) {
		
		if(isUnreachable()) {
			addWarning(expr.paren, "Unreachable code", true);
			return null;
		}
		
		check(expr.callee);
		for(Expr argument : expr.arguments)
			check(argument);
		
		return null;
		
	}
	
	@Override
	public Void visitGetExpr(Get expr) {
		
		if(isUnreachable()) {
			addWarning(expr.name, "Unreachable code", true);
			return null;
		}
		
		check(expr.object);
		
		return null;
		
	}
	
	@Override
	public Void visitGroupingExpr(Grouping expr) {
		
		check(expr.expression);
		
		return null;
		
	}
	
	@Override
	public Void visitLambdaExpr(Lambda expr) {
		
		if(isUnreachable()) {
			addWarning(expr.start, "Unreachable code", true);
			return null;
		}
		
		returned.initialize();
		exited.startLevel();
		thrown.startLevel();
		
		checkFunction(expr);
		
		returned.end();
		exited.endLevel();
		thrown.endLevel();
		
		return null;
		
	}
	
	@Override
	public Void visitLiteralExpr(Literal expr) {
		
		return null;
	}
	
	@Override
	public Void visitLogicalExpr(Logical expr) {
		
		if(isUnreachable()) {
			addWarning(expr.operator, "Unreachable code", true);
			return null;
		}
		
		check(expr.left);
		check(expr.right);
		
		return null;
		
	}
	
	@Override
	public Void visitSetExpr(Set expr) {
		
		if(isUnreachable()) {
			addWarning(expr.name, "Unreachable code", true);
			return null;
		}
		
		if(expr.object instanceof Expr.This)
			classVariables.get(currentClass).add(expr.name);
		
		check(expr.object);
		check(expr.value);
		
		return null;
		
	}
	
	@Override
	public Void visitSuperExpr(Super expr) {
		
		if(isUnreachable()) {
			addWarning(expr.keyword, "Unreachable code", true);
			return null;
		}
		
		return null;
		
	}
	
	@Override
	public Void visitThisExpr(This expr) {
		
		if(isUnreachable()) {
			addWarning(expr.keyword, "Unreachable code", true);
			return null;
		}
		
		return null;
		
	}
	
	@Override
	public Void visitUnaryExpr(Unary expr) {
		
		if(isUnreachable()) {
			addWarning(expr.operator, "Unreachable code", true);
			return null;
		}
		
		check(expr.right);
		
		return null;
		
	}
	
	@Override
	public Void visitVariableExpr(Variable expr) {
		
		if(isUnreachable()) {
			addWarning(expr.name, "Unreachable code", true);
			return null;
		}
		
		markAsUsed(expr.name);
		
		return null;
		
	}
	
	void check(List<Stmt> statements) {
		
		for(Stmt statement : statements)
			check(statement);
	}
	
	private void check(Stmt stmt) {
		
		stmt.accept(this);
		
	}
	
	private void check(Expr expr) {
		
		expr.accept(this);
		
	}
	
	private void markAsUsed(Token name) {
		
		for(int i = varsUsed.size() - 1; i >= 0; i--) {
			if(varsUsed.get(i).containsKey(name)) {
				varsUsed.get(i).put(name, true);
				return;
			}
		}
		
	}
	
	private void checkFunction(Function function, FunctionType type) {
		
		if(type == FunctionType.METHOD)
			classMethods.get(currentClass).add(function.name);
		
		beginScope();
		for(Token param : function.parameters)
			define(param);
		
		check(function.body);
		
		endScope();
		
	}
	
	private void checkFunction(Lambda function) {
		
		beginScope();
		for(Token param : function.parameters)
			define(param);
		
		check(function.body);
		
		endScope();
		
	}
	
	private void checkClassOverwrite() {
		
		for(Token klass : classVariables.keySet()) {
			for(Token var : classVariables.get(klass)) {
				Token currKlass = klass;
				while(currKlass != null) {
					if(classMethods.get(currKlass).contains(var)) {
						addWarning(var, "Overwriting method of same name in class '" + currKlass.lexeme + "'");
						break;
					}
					
					currKlass = inheritance.get(currKlass);
				}
			}
		}
		
	}
	
	private void define(Token name) {
		
		if(declared.peek().contains(name))
			addWarning(name, "Variable has already been declared in this scope");
		else
			declared.peek().add(name);
		
		if(varsUsed.isEmpty())
			return;
		varsUsed.peek().put(name, false);
		
	}
	
	private void beginScope() {
		
		varsUsed.push(new HashMap<>());
		declared.push(new ArrayList<>());
		
	}
	
	private void endScope() {
		
		Map<Token, Boolean> used = varsUsed.pop();
		
		for(Token var : used.keySet())
			if(!used.get(var))
				addWarning(var, "Local variable never used");
		
		declared.pop();
			
	}
	
	private boolean isUnreachable() {
		return returned.hasBeenSeen() || exited.hasBeenSeen() || thrown.hasBeenSeen();
	}
	
	private void addWarning(Token token, String message) {
		warnings.add(new Warning(token, message));
	}
	
	private void addWarning(Token token, String message, boolean useLine) {
		warnings.add(new Warning(token, message, useLine));
	}
	
	private enum FunctionType {
		FUNCTION, METHOD
	}
	
	private class Warning {
		final Token token;
		final String message;
		final boolean useLine;
		
		Warning(Token token, String message) {
			this.token = token;
			this.message = message;
			this.useLine = false;
		}
		
		Warning(Token token, String message, boolean useLine) {
			this.token = token;
			this.message = message;
			this.useLine = useLine;
		}
	}
	
	private class ControlFlowTracker {
		private final Stack<Boolean> currentLevel;
		
		ControlFlowTracker() {
			currentLevel = new Stack<>();
		}
		
		void initialize() {
			currentLevel.push(false);
		}
		
		void end() {
			currentLevel.pop();
		}
		
		void startLevel() {
			if(!currentLevel.isEmpty())
				currentLevel.push(currentLevel.peek());
			else
				currentLevel.push(false);
		}
		
		void endLevel() {
			currentLevel.pop();
		}
		
		void markAsSeen() {
			if(!currentLevel.isEmpty()) {
				currentLevel.pop();
				currentLevel.push(true);
			}
		}
		
		boolean hasBeenSeen() {
			if(currentLevel.isEmpty())
				return false;
			return currentLevel.peek();
		}
		
	}
	
}
