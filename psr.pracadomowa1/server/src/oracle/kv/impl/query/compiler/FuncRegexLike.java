/*-
 * Copyright (C) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This file was distributed by Oracle as part of a version of Oracle NoSQL
 * Database made available at:
 *
 * http://www.oracle.com/technetwork/database/database-technologies/nosqldb/downloads/index.html
 *
 * Please see the LICENSE file included in the top-level directory of the
 * appropriate version of Oracle NoSQL Database for a copy of the license and
 * additional information.
 */

package oracle.kv.impl.query.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import oracle.kv.impl.api.table.FieldDefImpl;
import oracle.kv.impl.api.table.FieldValueImpl;
import oracle.kv.impl.query.QueryException;
import oracle.kv.impl.query.compiler.Expr.ConstKind;
import oracle.kv.impl.query.compiler.FunctionLib.FuncCode;
import oracle.kv.impl.query.runtime.FuncRegexLikeIter;
import oracle.kv.impl.query.runtime.PlanIter;
import oracle.kv.impl.query.types.ExprType;
import oracle.kv.impl.query.types.TypeManager;

/*
 * boolean regex_like(any*, string, string)
 */
public class FuncRegexLike extends Function {
    
    public static String FLAGS = "dixlsucU";

    public static FuncRegexLike getFuncRegexLike() {
        ArrayList<ExprType> regexLikeTypes = new ArrayList<ExprType>(3);
        regexLikeTypes.add(TypeManager.ANY_STAR());
        regexLikeTypes.add(TypeManager.STRING_ONE());
        regexLikeTypes.add(TypeManager.STRING_ONE());
        return new FuncRegexLike(regexLikeTypes);
    }

    private FuncRegexLike(ArrayList<ExprType> regexLikeTypes) {
        super(FuncCode.FN_REGEX_LIKE,
              "regex_like",
              regexLikeTypes,
              TypeManager.BOOLEAN_ONE(),
              true);
    }

    @Override
    boolean mayReturnNULL(ExprFuncCall fncall) {
        return true;
    }
    
    @Override
    Expr normalizeCall(ExprFuncCall fncall) {

        boolean isPatternConst;
        boolean isFlagsConst = true;
        String origFlagsString = null;
        
        int numargs = fncall.getNumArgs();

        if (numargs < 2 || numargs > 3) {
            throw new QueryException(
                "The number of parameters specified for " +
                "the regex_like function is invalid.");
        }

        Expr strCastExpr = ExprCast.create(fncall.getQCB(), fncall.getSctx(),
                                           fncall.getLocation(),
                                           fncall.getArg(0),
                                           FieldDefImpl.stringDef,
                                           ExprType.Quantifier.STAR);
        fncall.setArg(0, strCastExpr, false);

        isPatternConst = ConstKind.isCompileConst(fncall.getArg(1));
        int flags = 0;

        if (numargs == 3) {
            isFlagsConst = ConstKind.isCompileConst(fncall.getArg(2));
            if (isFlagsConst) {
                ExprConst ec = (ExprConst)fncall.getArg(2);
                FieldValueImpl fv = ec.getValue();
                origFlagsString = fv.asString().get();
                flags = convertFlags(origFlagsString);
            }
        }

        if (isPatternConst && isFlagsConst) {
            ExprConst ec = (ExprConst)fncall.getArg(1);
            String pattern = ec.getValue().asString().get();
           
            verifyPattern(pattern);
            try {
                if (flags == 0) {
                    Pattern.compile(pattern);
                } else {
                    Pattern.compile(pattern, flags);
                }
            } catch (PatternSyntaxException e) {
               throw new QueryException(
                   "The pattern [" + pattern +
                   "] specified for the regex_like function is invalid.");
            } catch (IllegalArgumentException e) {
                throw new QueryException(
                    "The flags parameter [" + origFlagsString + "]  " +
                    "specified for the regex_like function is invalid. " +
                    "Valid flag parameter values are [" + FLAGS +"].");
            }
        }
        
        return fncall;
    }

    @Override
    PlanIter codegen(CodeGenerator codegen,
                     ExprFuncCall fncall,
                     PlanIter[] argIters) {

        int resultReg = codegen.allocateResultReg(fncall);
        return new FuncRegexLikeIter(fncall, resultReg, argIters);
    }
    
    public static int convertFlags(String sf) {
        int flags = 0;
        if (sf == null) {
            return flags;
        }
        for (int i = 0; i < sf.length(); i++) {
            switch (sf.charAt(i)) {
                case 'd' : 
                    flags |= Pattern.UNIX_LINES;
                    break;
                case 'i' : 
                    flags |= Pattern.CASE_INSENSITIVE;
                    break;
                case 'x' : 
                    flags |= Pattern.COMMENTS;
                    break;
                case 'l' : 
                    flags |= Pattern.LITERAL;
                    break;
                case 's' : 
                    flags |= Pattern.DOTALL;
                    break;
                case 'u' : 
                    flags |= Pattern.UNICODE_CASE;
                    break;
                case 'c' : 
                    flags |= Pattern.CANON_EQ;
                    break;
                case 'U' : 
                    flags |= Pattern.UNICODE_CHARACTER_CLASS;
                    break;
                /* MULTILINE mode is taken out since we restrict the
                 * regex pattern. The restricted pattern renders this mode
                 * meaningless.
                 * Add back in the ^ or $ is supported in the expression.
                case 'm' : 
                    flags |= Pattern.MULTILINE;
                    break;
                */
                default : 
                    if (!Character.isWhitespace(sf.charAt(i))) {
                        throw new IllegalArgumentException(
                            "The flags parameter [" + sf.charAt(i) + "] " +
                            "specified for the regex_like function is " +
                            "invalid. Valid flag parameter values are [" + 
                            FLAGS +"].");
                    }
                    break;
            }
        }
        return flags;
    }
    
    public static Map<String, String> validRegexAlpha =
        new HashMap<String, String>();

    static {
        validRegexAlpha.put("Q", "Q");
        validRegexAlpha.put("E", "E");
        validRegexAlpha.put("t", "t");
        validRegexAlpha.put("n", "n");
        validRegexAlpha.put("r", "r");
        validRegexAlpha.put("f", "f");
        validRegexAlpha.put("a", "a");
        validRegexAlpha.put("e", "e");
        validRegexAlpha.put("c", "c");
        validRegexAlpha.put("x", "x");
        validRegexAlpha.put("u", "u");

    }
    
    /*
     * The following constructs are not supported. It should be noted
     * that Pattern treats certain characters !-<>=: as context sensitive 
     * if they occur within (). For our purposes, it is enough to flag the ().
     */
    public static Map<String, String> invalidRegexOps = 
        new HashMap<String, String>();
    static {
        invalidRegexOps.put("^", "^");
        invalidRegexOps.put("$", "$");
        invalidRegexOps.put("[", "[");
        invalidRegexOps.put("]", "]");
        invalidRegexOps.put("{", "{");
        invalidRegexOps.put("}", "}");
        invalidRegexOps.put("+", "+");
        invalidRegexOps.put("?", "?");
        invalidRegexOps.put("|", "|");
        invalidRegexOps.put("(", "(");
        invalidRegexOps.put(")", ")");
    }

    /*
     * This method verifies the pattern string only contains
     * the supported regex pattern constructs. It does not verify that
     * the string is a valid regex string.
     */
    public static void verifyPattern(String pattern) {
        final int MAX_PATTERN_SIZE = 512;
        
        if (pattern.length() > MAX_PATTERN_SIZE) {
            throw new IllegalArgumentException(
                "The regex_like pattern parameter [" + pattern + "] " +
                "exceeds the maximum length of " + MAX_PATTERN_SIZE +
                " characters.");
        }
        
        boolean escState = false;
        int pos = 0;
        
        while (pos < pattern.length()) {
            if (pattern.charAt(pos) == '\\') {
                if (escState) {
                    escState = false;
                } else {
                    escState = true;
                }
            } else {
                if (escState) {
                    if (Character.isLetter(pattern.charAt(pos))) {
                        if (validRegexAlpha.get(
                                String.valueOf(pattern.charAt(pos))) == null) {
                            throw new IllegalArgumentException(
                                "The pattern parameter [" + pattern + "] " +
                                "contains an unsupported regular expression.");
                        }
                        
                        /* check for  \x{nnn} */
                        if (pattern.charAt(pos) == 'x') {
                            if ((pos + 1) < pattern.length() && 
                                 pattern.charAt(pos + 1) == '{') {
                                pos++;
                                while (pos < pattern.length() &&
                                       pattern.charAt(pos) != '}') {
                                    pos++;
                                }
                            }
                        } else if (pattern.charAt(pos) == 'Q') {
                            /* in quotation look for \E */
                            pos++;
                            while ((pos + 1) < pattern.length()) { 
                                if (pattern.charAt(pos) == '\\' &&
                                   pattern.charAt(pos + 1) == 'E') {
                                    pos++;
                                    break;
                                }
                                pos++;
                            }
                        }
                    }
                    escState = false;
                } else { /* not escaped */
                    if (invalidRegexOps.get(
                            String.valueOf(pattern.charAt(pos))) != null) {
                        throw new IllegalArgumentException(
                            "The pattern parameter [" + pattern + "] " +
                            "contains an unsupported regular expression.");
                    }
                }
            }
            pos++;
        }
    }
}
