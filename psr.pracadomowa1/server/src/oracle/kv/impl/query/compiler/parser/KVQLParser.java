// Generated from /home/markos/KVS/kv_20_3/kvstore/src/oracle/kv/impl/query/compiler/parser/KVQL.g4 by ANTLR 4.8
package oracle.kv.impl.query.compiler.parser;
import oracle.kv.shaded.org.antlr.v4.runtime.atn.*;
import oracle.kv.shaded.org.antlr.v4.runtime.dfa.DFA;
import oracle.kv.shaded.org.antlr.v4.runtime.*;
import oracle.kv.shaded.org.antlr.v4.runtime.misc.*;
import oracle.kv.shaded.org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class KVQLParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.8", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, VARNAME=3, ACCOUNT=4, ADD=5, ADMIN=6, ALL=7, ALTER=8, 
		ALWAYS=9, ANCESTORS=10, AND=11, AS=12, ASC=13, BY=14, CACHE=15, CASE=16, 
		CASCADE=17, CAST=18, COMMENT=19, COUNT=20, CREATE=21, CYCLE=22, DAYS=23, 
		DECLARE=24, DEFAULT=25, DELETE=26, DESC=27, DESCENDANTS=28, DESCRIBE=29, 
		DISTINCT=30, DROP=31, ELEMENTOF=32, ELSE=33, END=34, ES_SHARDS=35, ES_REPLICAS=36, 
		EXISTS=37, EXTRACT=38, FIRST=39, FORCE_INDEX=40, FORCE_PRIMARY_INDEX=41, 
		FROM=42, FULLTEXT=43, GENERATED=44, GRANT=45, GROUP=46, HOURS=47, IDENTIFIED=48, 
		IDENTITY=49, IF=50, IN=51, INCREMENT=52, INDEX=53, INDEXES=54, INSERT=55, 
		INTO=56, IS=57, JSON=58, JOIN=59, KEY=60, KEYOF=61, KEYS=62, LAST=63, 
		LEFT=64, LIFETIME=65, LIMIT=66, LOCAL=67, LOCK=68, MAXVALUE=69, MINUTES=70, 
		MINVALUE=71, MODIFY=72, NAMESPACE=73, NAMESPACES=74, NESTED=75, NO=76, 
		NOT=77, NULLS=78, OFFSET=79, OF=80, ON=81, ONLY=82, OR=83, ORDER=84, OUTER=85, 
		OVERRIDE=86, PASSWORD=87, PREFER_INDEXES=88, PREFER_PRIMARY_INDEX=89, 
		PRIMARY=90, PUT=91, REGION=92, REGIONS=93, REMOVE=94, RETURNING=95, REVOKE=96, 
		ROLE=97, ROLES=98, SECONDS=99, SELECT=100, SEQ_TRANSFORM=101, SET=102, 
		SHARD=103, SHOW=104, START=105, TABLE=106, TABLES=107, THEN=108, TO=109, 
		TTL=110, TYPE=111, UNLOCK=112, UPDATE=113, UPSERT=114, USER=115, USERS=116, 
		USING=117, VALUES=118, WHEN=119, WHERE=120, WITH=121, UUID=122, ALL_PRIVILEGES=123, 
		IDENTIFIED_EXTERNALLY=124, PASSWORD_EXPIRE=125, RETAIN_CURRENT_PASSWORD=126, 
		CLEAR_RETAINED_PASSWORD=127, LEFT_OUTER_JOIN=128, ARRAY_T=129, BINARY_T=130, 
		BOOLEAN_T=131, DOUBLE_T=132, ENUM_T=133, FLOAT_T=134, GEOMETRY_T=135, 
		INTEGER_T=136, LONG_T=137, MAP_T=138, NUMBER_T=139, POINT_T=140, RECORD_T=141, 
		STRING_T=142, TIMESTAMP_T=143, ANY_T=144, ANYATOMIC_T=145, ANYJSONATOMIC_T=146, 
		ANYRECORD_T=147, SCALAR_T=148, SEMI=149, COMMA=150, COLON=151, LP=152, 
		RP=153, LBRACK=154, RBRACK=155, LBRACE=156, RBRACE=157, STAR=158, DOT=159, 
		DOLLAR=160, QUESTION_MARK=161, LT=162, LTE=163, GT=164, GTE=165, EQ=166, 
		NEQ=167, LT_ANY=168, LTE_ANY=169, GT_ANY=170, GTE_ANY=171, EQ_ANY=172, 
		NEQ_ANY=173, PLUS=174, MINUS=175, IDIV=176, RDIV=177, CONCAT=178, NULL=179, 
		FALSE=180, TRUE=181, INT=182, FLOAT=183, NUMBER=184, DSTRING=185, STRING=186, 
		SYSDOLAR=187, ID=188, BAD_ID=189, WS=190, C_COMMENT=191, LINE_COMMENT=192, 
		LINE_COMMENT1=193, UnrecognizedToken=194;
	public static final int
		RULE_parse = 0, RULE_statement = 1, RULE_query = 2, RULE_prolog = 3, RULE_var_decl = 4, 
		RULE_expr = 5, RULE_sfw_expr = 6, RULE_from_clause = 7, RULE_nested_tables = 8, 
		RULE_ancestor_tables = 9, RULE_descendant_tables = 10, RULE_left_outer_join_tables = 11, 
		RULE_left_outer_join_table = 12, RULE_from_table = 13, RULE_aliased_table_name = 14, 
		RULE_tab_alias = 15, RULE_where_clause = 16, RULE_select_clause = 17, 
		RULE_select_list = 18, RULE_hints = 19, RULE_hint = 20, RULE_col_alias = 21, 
		RULE_orderby_clause = 22, RULE_sort_spec = 23, RULE_groupby_clause = 24, 
		RULE_limit_clause = 25, RULE_offset_clause = 26, RULE_or_expr = 27, RULE_and_expr = 28, 
		RULE_not_expr = 29, RULE_is_null_expr = 30, RULE_cond_expr = 31, RULE_comp_expr = 32, 
		RULE_comp_op = 33, RULE_any_op = 34, RULE_in_expr = 35, RULE_in1_expr = 36, 
		RULE_in1_left_op = 37, RULE_in1_expr_list = 38, RULE_in2_expr = 39, RULE_in3_expr = 40, 
		RULE_exists_expr = 41, RULE_is_of_type_expr = 42, RULE_concatenate_expr = 43, 
		RULE_add_expr = 44, RULE_multiply_expr = 45, RULE_unary_expr = 46, RULE_path_expr = 47, 
		RULE_map_step = 48, RULE_map_field_step = 49, RULE_map_filter_step = 50, 
		RULE_array_step = 51, RULE_array_slice_step = 52, RULE_array_filter_step = 53, 
		RULE_primary_expr = 54, RULE_column_ref = 55, RULE_const_expr = 56, RULE_var_ref = 57, 
		RULE_array_constructor = 58, RULE_map_constructor = 59, RULE_transform_expr = 60, 
		RULE_transform_input_expr = 61, RULE_func_call = 62, RULE_count_star = 63, 
		RULE_case_expr = 64, RULE_cast_expr = 65, RULE_parenthesized_expr = 66, 
		RULE_extract_expr = 67, RULE_insert_statement = 68, RULE_insert_returning_clause = 69, 
		RULE_insert_clause = 70, RULE_insert_ttl_clause = 71, RULE_update_statement = 72, 
		RULE_update_returning_clause = 73, RULE_update_clause = 74, RULE_set_clause = 75, 
		RULE_add_clause = 76, RULE_put_clause = 77, RULE_remove_clause = 78, RULE_ttl_clause = 79, 
		RULE_target_expr = 80, RULE_pos_expr = 81, RULE_delete_statement = 82, 
		RULE_delete_returning_clause = 83, RULE_quantified_type_def = 84, RULE_type_def = 85, 
		RULE_record_def = 86, RULE_field_def = 87, RULE_default_def = 88, RULE_default_value = 89, 
		RULE_not_null = 90, RULE_map_def = 91, RULE_array_def = 92, RULE_integer_def = 93, 
		RULE_json_def = 94, RULE_float_def = 95, RULE_string_def = 96, RULE_enum_def = 97, 
		RULE_boolean_def = 98, RULE_binary_def = 99, RULE_timestamp_def = 100, 
		RULE_any_def = 101, RULE_anyAtomic_def = 102, RULE_anyJsonAtomic_def = 103, 
		RULE_anyRecord_def = 104, RULE_id_path = 105, RULE_table_id_path = 106, 
		RULE_table_id = 107, RULE_name_path = 108, RULE_field_name = 109, RULE_create_namespace_statement = 110, 
		RULE_drop_namespace_statement = 111, RULE_region_name = 112, RULE_create_region_statement = 113, 
		RULE_drop_region_statement = 114, RULE_set_local_region_statement = 115, 
		RULE_create_table_statement = 116, RULE_table_name = 117, RULE_namespace = 118, 
		RULE_table_def = 119, RULE_column_def = 120, RULE_key_def = 121, RULE_shard_key_def = 122, 
		RULE_id_list_with_size = 123, RULE_id_with_size = 124, RULE_storage_size = 125, 
		RULE_table_options = 126, RULE_ttl_def = 127, RULE_region_names = 128, 
		RULE_regions_def = 129, RULE_add_region_def = 130, RULE_drop_region_def = 131, 
		RULE_identity_def = 132, RULE_sequence_options = 133, RULE_uuid_def = 134, 
		RULE_alter_table_statement = 135, RULE_alter_def = 136, RULE_alter_field_statements = 137, 
		RULE_add_field_statement = 138, RULE_drop_field_statement = 139, RULE_modify_field_statement = 140, 
		RULE_schema_path = 141, RULE_init_schema_path_step = 142, RULE_schema_path_step = 143, 
		RULE_drop_table_statement = 144, RULE_create_index_statement = 145, RULE_index_name = 146, 
		RULE_index_path_list = 147, RULE_index_path = 148, RULE_keys_expr = 149, 
		RULE_values_expr = 150, RULE_path_type = 151, RULE_create_text_index_statement = 152, 
		RULE_fts_field_list = 153, RULE_fts_path_list = 154, RULE_fts_path = 155, 
		RULE_es_properties = 156, RULE_es_property_assignment = 157, RULE_drop_index_statement = 158, 
		RULE_describe_statement = 159, RULE_schema_path_list = 160, RULE_show_statement = 161, 
		RULE_create_user_statement = 162, RULE_create_role_statement = 163, RULE_alter_user_statement = 164, 
		RULE_drop_user_statement = 165, RULE_drop_role_statement = 166, RULE_grant_statement = 167, 
		RULE_revoke_statement = 168, RULE_identifier_or_string = 169, RULE_identified_clause = 170, 
		RULE_create_user_identified_clause = 171, RULE_by_password = 172, RULE_password_lifetime = 173, 
		RULE_reset_password_clause = 174, RULE_account_lock = 175, RULE_grant_roles = 176, 
		RULE_grant_system_privileges = 177, RULE_grant_object_privileges = 178, 
		RULE_revoke_roles = 179, RULE_revoke_system_privileges = 180, RULE_revoke_object_privileges = 181, 
		RULE_principal = 182, RULE_sys_priv_list = 183, RULE_priv_item = 184, 
		RULE_obj_priv_list = 185, RULE_object = 186, RULE_json_text = 187, RULE_jsobject = 188, 
		RULE_jsarray = 189, RULE_jspair = 190, RULE_jsvalue = 191, RULE_comment = 192, 
		RULE_duration = 193, RULE_time_unit = 194, RULE_number = 195, RULE_signed_int = 196, 
		RULE_string = 197, RULE_id_list = 198, RULE_id = 199;
	private static String[] makeRuleNames() {
		return new String[] {
			"parse", "statement", "query", "prolog", "var_decl", "expr", "sfw_expr", 
			"from_clause", "nested_tables", "ancestor_tables", "descendant_tables", 
			"left_outer_join_tables", "left_outer_join_table", "from_table", "aliased_table_name", 
			"tab_alias", "where_clause", "select_clause", "select_list", "hints", 
			"hint", "col_alias", "orderby_clause", "sort_spec", "groupby_clause", 
			"limit_clause", "offset_clause", "or_expr", "and_expr", "not_expr", "is_null_expr", 
			"cond_expr", "comp_expr", "comp_op", "any_op", "in_expr", "in1_expr", 
			"in1_left_op", "in1_expr_list", "in2_expr", "in3_expr", "exists_expr", 
			"is_of_type_expr", "concatenate_expr", "add_expr", "multiply_expr", "unary_expr", 
			"path_expr", "map_step", "map_field_step", "map_filter_step", "array_step", 
			"array_slice_step", "array_filter_step", "primary_expr", "column_ref", 
			"const_expr", "var_ref", "array_constructor", "map_constructor", "transform_expr", 
			"transform_input_expr", "func_call", "count_star", "case_expr", "cast_expr", 
			"parenthesized_expr", "extract_expr", "insert_statement", "insert_returning_clause", 
			"insert_clause", "insert_ttl_clause", "update_statement", "update_returning_clause", 
			"update_clause", "set_clause", "add_clause", "put_clause", "remove_clause", 
			"ttl_clause", "target_expr", "pos_expr", "delete_statement", "delete_returning_clause", 
			"quantified_type_def", "type_def", "record_def", "field_def", "default_def", 
			"default_value", "not_null", "map_def", "array_def", "integer_def", "json_def", 
			"float_def", "string_def", "enum_def", "boolean_def", "binary_def", "timestamp_def", 
			"any_def", "anyAtomic_def", "anyJsonAtomic_def", "anyRecord_def", "id_path", 
			"table_id_path", "table_id", "name_path", "field_name", "create_namespace_statement", 
			"drop_namespace_statement", "region_name", "create_region_statement", 
			"drop_region_statement", "set_local_region_statement", "create_table_statement", 
			"table_name", "namespace", "table_def", "column_def", "key_def", "shard_key_def", 
			"id_list_with_size", "id_with_size", "storage_size", "table_options", 
			"ttl_def", "region_names", "regions_def", "add_region_def", "drop_region_def", 
			"identity_def", "sequence_options", "uuid_def", "alter_table_statement", 
			"alter_def", "alter_field_statements", "add_field_statement", "drop_field_statement", 
			"modify_field_statement", "schema_path", "init_schema_path_step", "schema_path_step", 
			"drop_table_statement", "create_index_statement", "index_name", "index_path_list", 
			"index_path", "keys_expr", "values_expr", "path_type", "create_text_index_statement", 
			"fts_field_list", "fts_path_list", "fts_path", "es_properties", "es_property_assignment", 
			"drop_index_statement", "describe_statement", "schema_path_list", "show_statement", 
			"create_user_statement", "create_role_statement", "alter_user_statement", 
			"drop_user_statement", "drop_role_statement", "grant_statement", "revoke_statement", 
			"identifier_or_string", "identified_clause", "create_user_identified_clause", 
			"by_password", "password_lifetime", "reset_password_clause", "account_lock", 
			"grant_roles", "grant_system_privileges", "grant_object_privileges", 
			"revoke_roles", "revoke_system_privileges", "revoke_object_privileges", 
			"principal", "sys_priv_list", "priv_item", "obj_priv_list", "object", 
			"json_text", "jsobject", "jsarray", "jspair", "jsvalue", "comment", "duration", 
			"time_unit", "number", "signed_int", "string", "id_list", "id"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'/*+'", "'*/'", null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, "'count'", null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, "'seq_transform'", null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, "';'", "','", "':'", 
			"'('", "')'", "'['", "']'", "'{'", "'}'", "'*'", "'.'", "'$'", "'?'", 
			"'<'", "'<='", "'>'", "'>='", "'='", "'!='", null, null, null, null, 
			null, null, "'+'", "'-'", "'/'", null, "'||'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, "VARNAME", "ACCOUNT", "ADD", "ADMIN", "ALL", "ALTER", 
			"ALWAYS", "ANCESTORS", "AND", "AS", "ASC", "BY", "CACHE", "CASE", "CASCADE", 
			"CAST", "COMMENT", "COUNT", "CREATE", "CYCLE", "DAYS", "DECLARE", "DEFAULT", 
			"DELETE", "DESC", "DESCENDANTS", "DESCRIBE", "DISTINCT", "DROP", "ELEMENTOF", 
			"ELSE", "END", "ES_SHARDS", "ES_REPLICAS", "EXISTS", "EXTRACT", "FIRST", 
			"FORCE_INDEX", "FORCE_PRIMARY_INDEX", "FROM", "FULLTEXT", "GENERATED", 
			"GRANT", "GROUP", "HOURS", "IDENTIFIED", "IDENTITY", "IF", "IN", "INCREMENT", 
			"INDEX", "INDEXES", "INSERT", "INTO", "IS", "JSON", "JOIN", "KEY", "KEYOF", 
			"KEYS", "LAST", "LEFT", "LIFETIME", "LIMIT", "LOCAL", "LOCK", "MAXVALUE", 
			"MINUTES", "MINVALUE", "MODIFY", "NAMESPACE", "NAMESPACES", "NESTED", 
			"NO", "NOT", "NULLS", "OFFSET", "OF", "ON", "ONLY", "OR", "ORDER", "OUTER", 
			"OVERRIDE", "PASSWORD", "PREFER_INDEXES", "PREFER_PRIMARY_INDEX", "PRIMARY", 
			"PUT", "REGION", "REGIONS", "REMOVE", "RETURNING", "REVOKE", "ROLE", 
			"ROLES", "SECONDS", "SELECT", "SEQ_TRANSFORM", "SET", "SHARD", "SHOW", 
			"START", "TABLE", "TABLES", "THEN", "TO", "TTL", "TYPE", "UNLOCK", "UPDATE", 
			"UPSERT", "USER", "USERS", "USING", "VALUES", "WHEN", "WHERE", "WITH", 
			"UUID", "ALL_PRIVILEGES", "IDENTIFIED_EXTERNALLY", "PASSWORD_EXPIRE", 
			"RETAIN_CURRENT_PASSWORD", "CLEAR_RETAINED_PASSWORD", "LEFT_OUTER_JOIN", 
			"ARRAY_T", "BINARY_T", "BOOLEAN_T", "DOUBLE_T", "ENUM_T", "FLOAT_T", 
			"GEOMETRY_T", "INTEGER_T", "LONG_T", "MAP_T", "NUMBER_T", "POINT_T", 
			"RECORD_T", "STRING_T", "TIMESTAMP_T", "ANY_T", "ANYATOMIC_T", "ANYJSONATOMIC_T", 
			"ANYRECORD_T", "SCALAR_T", "SEMI", "COMMA", "COLON", "LP", "RP", "LBRACK", 
			"RBRACK", "LBRACE", "RBRACE", "STAR", "DOT", "DOLLAR", "QUESTION_MARK", 
			"LT", "LTE", "GT", "GTE", "EQ", "NEQ", "LT_ANY", "LTE_ANY", "GT_ANY", 
			"GTE_ANY", "EQ_ANY", "NEQ_ANY", "PLUS", "MINUS", "IDIV", "RDIV", "CONCAT", 
			"NULL", "FALSE", "TRUE", "INT", "FLOAT", "NUMBER", "DSTRING", "STRING", 
			"SYSDOLAR", "ID", "BAD_ID", "WS", "C_COMMENT", "LINE_COMMENT", "LINE_COMMENT1", 
			"UnrecognizedToken"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "KVQL.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public KVQLParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class ParseContext extends ParserRuleContext {
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public TerminalNode EOF() { return getToken(KVQLParser.EOF, 0); }
		public ParseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parse; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterParse(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitParse(this);
		}
	}

	public final ParseContext parse() throws RecognitionException {
		ParseContext _localctx = new ParseContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_parse);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(400);
			statement();
			setState(401);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StatementContext extends ParserRuleContext {
		public QueryContext query() {
			return getRuleContext(QueryContext.class,0);
		}
		public Insert_statementContext insert_statement() {
			return getRuleContext(Insert_statementContext.class,0);
		}
		public Update_statementContext update_statement() {
			return getRuleContext(Update_statementContext.class,0);
		}
		public Delete_statementContext delete_statement() {
			return getRuleContext(Delete_statementContext.class,0);
		}
		public Create_table_statementContext create_table_statement() {
			return getRuleContext(Create_table_statementContext.class,0);
		}
		public Create_index_statementContext create_index_statement() {
			return getRuleContext(Create_index_statementContext.class,0);
		}
		public Create_user_statementContext create_user_statement() {
			return getRuleContext(Create_user_statementContext.class,0);
		}
		public Create_role_statementContext create_role_statement() {
			return getRuleContext(Create_role_statementContext.class,0);
		}
		public Create_namespace_statementContext create_namespace_statement() {
			return getRuleContext(Create_namespace_statementContext.class,0);
		}
		public Create_region_statementContext create_region_statement() {
			return getRuleContext(Create_region_statementContext.class,0);
		}
		public Drop_index_statementContext drop_index_statement() {
			return getRuleContext(Drop_index_statementContext.class,0);
		}
		public Drop_namespace_statementContext drop_namespace_statement() {
			return getRuleContext(Drop_namespace_statementContext.class,0);
		}
		public Drop_region_statementContext drop_region_statement() {
			return getRuleContext(Drop_region_statementContext.class,0);
		}
		public Create_text_index_statementContext create_text_index_statement() {
			return getRuleContext(Create_text_index_statementContext.class,0);
		}
		public Drop_role_statementContext drop_role_statement() {
			return getRuleContext(Drop_role_statementContext.class,0);
		}
		public Drop_user_statementContext drop_user_statement() {
			return getRuleContext(Drop_user_statementContext.class,0);
		}
		public Alter_table_statementContext alter_table_statement() {
			return getRuleContext(Alter_table_statementContext.class,0);
		}
		public Alter_user_statementContext alter_user_statement() {
			return getRuleContext(Alter_user_statementContext.class,0);
		}
		public Drop_table_statementContext drop_table_statement() {
			return getRuleContext(Drop_table_statementContext.class,0);
		}
		public Grant_statementContext grant_statement() {
			return getRuleContext(Grant_statementContext.class,0);
		}
		public Revoke_statementContext revoke_statement() {
			return getRuleContext(Revoke_statementContext.class,0);
		}
		public Describe_statementContext describe_statement() {
			return getRuleContext(Describe_statementContext.class,0);
		}
		public Set_local_region_statementContext set_local_region_statement() {
			return getRuleContext(Set_local_region_statementContext.class,0);
		}
		public Show_statementContext show_statement() {
			return getRuleContext(Show_statementContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitStatement(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(427);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,0,_ctx) ) {
			case 1:
				{
				setState(403);
				query();
				}
				break;
			case 2:
				{
				setState(404);
				insert_statement();
				}
				break;
			case 3:
				{
				setState(405);
				update_statement();
				}
				break;
			case 4:
				{
				setState(406);
				delete_statement();
				}
				break;
			case 5:
				{
				setState(407);
				create_table_statement();
				}
				break;
			case 6:
				{
				setState(408);
				create_index_statement();
				}
				break;
			case 7:
				{
				setState(409);
				create_user_statement();
				}
				break;
			case 8:
				{
				setState(410);
				create_role_statement();
				}
				break;
			case 9:
				{
				setState(411);
				create_namespace_statement();
				}
				break;
			case 10:
				{
				setState(412);
				create_region_statement();
				}
				break;
			case 11:
				{
				setState(413);
				drop_index_statement();
				}
				break;
			case 12:
				{
				setState(414);
				drop_namespace_statement();
				}
				break;
			case 13:
				{
				setState(415);
				drop_region_statement();
				}
				break;
			case 14:
				{
				setState(416);
				create_text_index_statement();
				}
				break;
			case 15:
				{
				setState(417);
				drop_role_statement();
				}
				break;
			case 16:
				{
				setState(418);
				drop_user_statement();
				}
				break;
			case 17:
				{
				setState(419);
				alter_table_statement();
				}
				break;
			case 18:
				{
				setState(420);
				alter_user_statement();
				}
				break;
			case 19:
				{
				setState(421);
				drop_table_statement();
				}
				break;
			case 20:
				{
				setState(422);
				grant_statement();
				}
				break;
			case 21:
				{
				setState(423);
				revoke_statement();
				}
				break;
			case 22:
				{
				setState(424);
				describe_statement();
				}
				break;
			case 23:
				{
				setState(425);
				set_local_region_statement();
				}
				break;
			case 24:
				{
				setState(426);
				show_statement();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class QueryContext extends ParserRuleContext {
		public Sfw_exprContext sfw_expr() {
			return getRuleContext(Sfw_exprContext.class,0);
		}
		public PrologContext prolog() {
			return getRuleContext(PrologContext.class,0);
		}
		public QueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_query; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitQuery(this);
		}
	}

	public final QueryContext query() throws RecognitionException {
		QueryContext _localctx = new QueryContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_query);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(430);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DECLARE) {
				{
				setState(429);
				prolog();
				}
			}

			setState(432);
			sfw_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PrologContext extends ParserRuleContext {
		public TerminalNode DECLARE() { return getToken(KVQLParser.DECLARE, 0); }
		public List<Var_declContext> var_decl() {
			return getRuleContexts(Var_declContext.class);
		}
		public Var_declContext var_decl(int i) {
			return getRuleContext(Var_declContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(KVQLParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(KVQLParser.SEMI, i);
		}
		public PrologContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_prolog; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterProlog(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitProlog(this);
		}
	}

	public final PrologContext prolog() throws RecognitionException {
		PrologContext _localctx = new PrologContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_prolog);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(434);
			match(DECLARE);
			setState(435);
			var_decl();
			setState(436);
			match(SEMI);
			setState(442);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==VARNAME) {
				{
				{
				setState(437);
				var_decl();
				setState(438);
				match(SEMI);
				}
				}
				setState(444);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Var_declContext extends ParserRuleContext {
		public TerminalNode VARNAME() { return getToken(KVQLParser.VARNAME, 0); }
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public Var_declContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_var_decl; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterVar_decl(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitVar_decl(this);
		}
	}

	public final Var_declContext var_decl() throws RecognitionException {
		Var_declContext _localctx = new Var_declContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_var_decl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(445);
			match(VARNAME);
			setState(446);
			type_def();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExprContext extends ParserRuleContext {
		public Or_exprContext or_expr() {
			return getRuleContext(Or_exprContext.class,0);
		}
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitExpr(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		ExprContext _localctx = new ExprContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(448);
			or_expr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sfw_exprContext extends ParserRuleContext {
		public Select_clauseContext select_clause() {
			return getRuleContext(Select_clauseContext.class,0);
		}
		public From_clauseContext from_clause() {
			return getRuleContext(From_clauseContext.class,0);
		}
		public Where_clauseContext where_clause() {
			return getRuleContext(Where_clauseContext.class,0);
		}
		public Groupby_clauseContext groupby_clause() {
			return getRuleContext(Groupby_clauseContext.class,0);
		}
		public Orderby_clauseContext orderby_clause() {
			return getRuleContext(Orderby_clauseContext.class,0);
		}
		public Limit_clauseContext limit_clause() {
			return getRuleContext(Limit_clauseContext.class,0);
		}
		public Offset_clauseContext offset_clause() {
			return getRuleContext(Offset_clauseContext.class,0);
		}
		public Sfw_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sfw_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSfw_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSfw_expr(this);
		}
	}

	public final Sfw_exprContext sfw_expr() throws RecognitionException {
		Sfw_exprContext _localctx = new Sfw_exprContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_sfw_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(450);
			select_clause();
			setState(451);
			from_clause();
			setState(453);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(452);
				where_clause();
				}
			}

			setState(456);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==GROUP) {
				{
				setState(455);
				groupby_clause();
				}
			}

			setState(459);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ORDER) {
				{
				setState(458);
				orderby_clause();
				}
			}

			setState(462);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LIMIT) {
				{
				setState(461);
				limit_clause();
				}
			}

			setState(465);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OFFSET) {
				{
				setState(464);
				offset_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class From_clauseContext extends ParserRuleContext {
		public TerminalNode FROM() { return getToken(KVQLParser.FROM, 0); }
		public From_tableContext from_table() {
			return getRuleContext(From_tableContext.class,0);
		}
		public Nested_tablesContext nested_tables() {
			return getRuleContext(Nested_tablesContext.class,0);
		}
		public Left_outer_join_tablesContext left_outer_join_tables() {
			return getRuleContext(Left_outer_join_tablesContext.class,0);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> VARNAME() { return getTokens(KVQLParser.VARNAME); }
		public TerminalNode VARNAME(int i) {
			return getToken(KVQLParser.VARNAME, i);
		}
		public List<TerminalNode> AS() { return getTokens(KVQLParser.AS); }
		public TerminalNode AS(int i) {
			return getToken(KVQLParser.AS, i);
		}
		public From_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_from_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFrom_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFrom_clause(this);
		}
	}

	public final From_clauseContext from_clause() throws RecognitionException {
		From_clauseContext _localctx = new From_clauseContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_from_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(467);
			match(FROM);
			setState(471);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,8,_ctx) ) {
			case 1:
				{
				setState(468);
				from_table();
				}
				break;
			case 2:
				{
				setState(469);
				nested_tables();
				}
				break;
			case 3:
				{
				setState(470);
				left_outer_join_tables();
				}
				break;
			}
			setState(482);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(473);
				match(COMMA);
				setState(474);
				expr();
				{
				setState(476);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(475);
					match(AS);
					}
				}

				setState(478);
				match(VARNAME);
				}
				}
				}
				setState(484);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Nested_tablesContext extends ParserRuleContext {
		public TerminalNode NESTED() { return getToken(KVQLParser.NESTED, 0); }
		public TerminalNode TABLES() { return getToken(KVQLParser.TABLES, 0); }
		public List<TerminalNode> LP() { return getTokens(KVQLParser.LP); }
		public TerminalNode LP(int i) {
			return getToken(KVQLParser.LP, i);
		}
		public From_tableContext from_table() {
			return getRuleContext(From_tableContext.class,0);
		}
		public List<TerminalNode> RP() { return getTokens(KVQLParser.RP); }
		public TerminalNode RP(int i) {
			return getToken(KVQLParser.RP, i);
		}
		public TerminalNode ANCESTORS() { return getToken(KVQLParser.ANCESTORS, 0); }
		public Ancestor_tablesContext ancestor_tables() {
			return getRuleContext(Ancestor_tablesContext.class,0);
		}
		public TerminalNode DESCENDANTS() { return getToken(KVQLParser.DESCENDANTS, 0); }
		public Descendant_tablesContext descendant_tables() {
			return getRuleContext(Descendant_tablesContext.class,0);
		}
		public Nested_tablesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nested_tables; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterNested_tables(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitNested_tables(this);
		}
	}

	public final Nested_tablesContext nested_tables() throws RecognitionException {
		Nested_tablesContext _localctx = new Nested_tablesContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_nested_tables);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(485);
			match(NESTED);
			setState(486);
			match(TABLES);
			setState(487);
			match(LP);
			setState(488);
			from_table();
			setState(494);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ANCESTORS) {
				{
				setState(489);
				match(ANCESTORS);
				setState(490);
				match(LP);
				setState(491);
				ancestor_tables();
				setState(492);
				match(RP);
				}
			}

			setState(501);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DESCENDANTS) {
				{
				setState(496);
				match(DESCENDANTS);
				setState(497);
				match(LP);
				setState(498);
				descendant_tables();
				setState(499);
				match(RP);
				}
			}

			setState(503);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Ancestor_tablesContext extends ParserRuleContext {
		public List<From_tableContext> from_table() {
			return getRuleContexts(From_tableContext.class);
		}
		public From_tableContext from_table(int i) {
			return getRuleContext(From_tableContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Ancestor_tablesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ancestor_tables; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAncestor_tables(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAncestor_tables(this);
		}
	}

	public final Ancestor_tablesContext ancestor_tables() throws RecognitionException {
		Ancestor_tablesContext _localctx = new Ancestor_tablesContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_ancestor_tables);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(505);
			from_table();
			setState(510);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(506);
				match(COMMA);
				setState(507);
				from_table();
				}
				}
				setState(512);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Descendant_tablesContext extends ParserRuleContext {
		public List<From_tableContext> from_table() {
			return getRuleContexts(From_tableContext.class);
		}
		public From_tableContext from_table(int i) {
			return getRuleContext(From_tableContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Descendant_tablesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_descendant_tables; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDescendant_tables(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDescendant_tables(this);
		}
	}

	public final Descendant_tablesContext descendant_tables() throws RecognitionException {
		Descendant_tablesContext _localctx = new Descendant_tablesContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_descendant_tables);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(513);
			from_table();
			setState(518);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(514);
				match(COMMA);
				setState(515);
				from_table();
				}
				}
				setState(520);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Left_outer_join_tablesContext extends ParserRuleContext {
		public From_tableContext from_table() {
			return getRuleContext(From_tableContext.class,0);
		}
		public List<Left_outer_join_tableContext> left_outer_join_table() {
			return getRuleContexts(Left_outer_join_tableContext.class);
		}
		public Left_outer_join_tableContext left_outer_join_table(int i) {
			return getRuleContext(Left_outer_join_tableContext.class,i);
		}
		public Left_outer_join_tablesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_left_outer_join_tables; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterLeft_outer_join_tables(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitLeft_outer_join_tables(this);
		}
	}

	public final Left_outer_join_tablesContext left_outer_join_tables() throws RecognitionException {
		Left_outer_join_tablesContext _localctx = new Left_outer_join_tablesContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_left_outer_join_tables);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(521);
			from_table();
			setState(522);
			left_outer_join_table();
			setState(526);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LEFT_OUTER_JOIN) {
				{
				{
				setState(523);
				left_outer_join_table();
				}
				}
				setState(528);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Left_outer_join_tableContext extends ParserRuleContext {
		public TerminalNode LEFT_OUTER_JOIN() { return getToken(KVQLParser.LEFT_OUTER_JOIN, 0); }
		public From_tableContext from_table() {
			return getRuleContext(From_tableContext.class,0);
		}
		public Left_outer_join_tableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_left_outer_join_table; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterLeft_outer_join_table(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitLeft_outer_join_table(this);
		}
	}

	public final Left_outer_join_tableContext left_outer_join_table() throws RecognitionException {
		Left_outer_join_tableContext _localctx = new Left_outer_join_tableContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_left_outer_join_table);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(529);
			match(LEFT_OUTER_JOIN);
			setState(530);
			from_table();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class From_tableContext extends ParserRuleContext {
		public Aliased_table_nameContext aliased_table_name() {
			return getRuleContext(Aliased_table_nameContext.class,0);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public Or_exprContext or_expr() {
			return getRuleContext(Or_exprContext.class,0);
		}
		public From_tableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_from_table; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFrom_table(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFrom_table(this);
		}
	}

	public final From_tableContext from_table() throws RecognitionException {
		From_tableContext _localctx = new From_tableContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_from_table);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(532);
			aliased_table_name();
			setState(535);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ON) {
				{
				setState(533);
				match(ON);
				setState(534);
				or_expr(0);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Aliased_table_nameContext extends ParserRuleContext {
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public Tab_aliasContext tab_alias() {
			return getRuleContext(Tab_aliasContext.class,0);
		}
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public Aliased_table_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_aliased_table_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAliased_table_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAliased_table_name(this);
		}
	}

	public final Aliased_table_nameContext aliased_table_name() throws RecognitionException {
		Aliased_table_nameContext _localctx = new Aliased_table_nameContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_aliased_table_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(537);
			table_name();
			}
			setState(542);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
			case 1:
				{
				setState(539);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,17,_ctx) ) {
				case 1:
					{
					setState(538);
					match(AS);
					}
					break;
				}
				setState(541);
				tab_alias();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Tab_aliasContext extends ParserRuleContext {
		public TerminalNode VARNAME() { return getToken(KVQLParser.VARNAME, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Tab_aliasContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tab_alias; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTab_alias(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTab_alias(this);
		}
	}

	public final Tab_aliasContext tab_alias() throws RecognitionException {
		Tab_aliasContext _localctx = new Tab_aliasContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_tab_alias);
		try {
			setState(546);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VARNAME:
				enterOuterAlt(_localctx, 1);
				{
				setState(544);
				match(VARNAME);
				}
				break;
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case RDIV:
			case ID:
			case BAD_ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(545);
				id();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Where_clauseContext extends ParserRuleContext {
		public TerminalNode WHERE() { return getToken(KVQLParser.WHERE, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Where_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_where_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterWhere_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitWhere_clause(this);
		}
	}

	public final Where_clauseContext where_clause() throws RecognitionException {
		Where_clauseContext _localctx = new Where_clauseContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_where_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(548);
			match(WHERE);
			setState(549);
			expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Select_clauseContext extends ParserRuleContext {
		public TerminalNode SELECT() { return getToken(KVQLParser.SELECT, 0); }
		public Select_listContext select_list() {
			return getRuleContext(Select_listContext.class,0);
		}
		public Select_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_select_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSelect_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSelect_clause(this);
		}
	}

	public final Select_clauseContext select_clause() throws RecognitionException {
		Select_clauseContext _localctx = new Select_clauseContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_select_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(551);
			match(SELECT);
			setState(552);
			select_list();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Select_listContext extends ParserRuleContext {
		public TerminalNode STAR() { return getToken(KVQLParser.STAR, 0); }
		public HintsContext hints() {
			return getRuleContext(HintsContext.class,0);
		}
		public TerminalNode DISTINCT() { return getToken(KVQLParser.DISTINCT, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<Col_aliasContext> col_alias() {
			return getRuleContexts(Col_aliasContext.class);
		}
		public Col_aliasContext col_alias(int i) {
			return getRuleContext(Col_aliasContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Select_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_select_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSelect_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSelect_list(this);
		}
	}

	public final Select_listContext select_list() throws RecognitionException {
		Select_listContext _localctx = new Select_listContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_select_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(555);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(554);
				hints();
				}
			}

			setState(558);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				{
				setState(557);
				match(DISTINCT);
				}
				break;
			}
			setState(572);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STAR:
				{
				setState(560);
				match(STAR);
				}
				break;
			case VARNAME:
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case LP:
			case LBRACK:
			case LBRACE:
			case DOLLAR:
			case QUESTION_MARK:
			case PLUS:
			case MINUS:
			case RDIV:
			case NULL:
			case FALSE:
			case TRUE:
			case INT:
			case FLOAT:
			case NUMBER:
			case DSTRING:
			case STRING:
			case ID:
			case BAD_ID:
				{
				{
				setState(561);
				expr();
				setState(562);
				col_alias();
				setState(569);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(563);
					match(COMMA);
					setState(564);
					expr();
					setState(565);
					col_alias();
					}
					}
					setState(571);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class HintsContext extends ParserRuleContext {
		public List<HintContext> hint() {
			return getRuleContexts(HintContext.class);
		}
		public HintContext hint(int i) {
			return getRuleContext(HintContext.class,i);
		}
		public HintsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_hints; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterHints(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitHints(this);
		}
	}

	public final HintsContext hints() throws RecognitionException {
		HintsContext _localctx = new HintsContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_hints);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(574);
			match(T__0);
			setState(578);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((((_la - 40)) & ~0x3f) == 0 && ((1L << (_la - 40)) & ((1L << (FORCE_INDEX - 40)) | (1L << (FORCE_PRIMARY_INDEX - 40)) | (1L << (PREFER_INDEXES - 40)) | (1L << (PREFER_PRIMARY_INDEX - 40)))) != 0)) {
				{
				{
				setState(575);
				hint();
				}
				}
				setState(580);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(581);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class HintContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(KVQLParser.STRING, 0); }
		public TerminalNode PREFER_INDEXES() { return getToken(KVQLParser.PREFER_INDEXES, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public TerminalNode FORCE_INDEX() { return getToken(KVQLParser.FORCE_INDEX, 0); }
		public List<Index_nameContext> index_name() {
			return getRuleContexts(Index_nameContext.class);
		}
		public Index_nameContext index_name(int i) {
			return getRuleContext(Index_nameContext.class,i);
		}
		public TerminalNode PREFER_PRIMARY_INDEX() { return getToken(KVQLParser.PREFER_PRIMARY_INDEX, 0); }
		public TerminalNode FORCE_PRIMARY_INDEX() { return getToken(KVQLParser.FORCE_PRIMARY_INDEX, 0); }
		public HintContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_hint; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterHint(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitHint(this);
		}
	}

	public final HintContext hint() throws RecognitionException {
		HintContext _localctx = new HintContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_hint);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(610);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case PREFER_INDEXES:
				{
				{
				setState(583);
				match(PREFER_INDEXES);
				setState(584);
				match(LP);
				setState(585);
				table_name();
				setState(589);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (RDIV - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
					{
					{
					setState(586);
					index_name();
					}
					}
					setState(591);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(592);
				match(RP);
				}
				}
				break;
			case FORCE_INDEX:
				{
				{
				setState(594);
				match(FORCE_INDEX);
				setState(595);
				match(LP);
				setState(596);
				table_name();
				setState(597);
				index_name();
				setState(598);
				match(RP);
				}
				}
				break;
			case PREFER_PRIMARY_INDEX:
				{
				{
				setState(600);
				match(PREFER_PRIMARY_INDEX);
				setState(601);
				match(LP);
				setState(602);
				table_name();
				setState(603);
				match(RP);
				}
				}
				break;
			case FORCE_PRIMARY_INDEX:
				{
				{
				setState(605);
				match(FORCE_PRIMARY_INDEX);
				setState(606);
				match(LP);
				setState(607);
				table_name();
				setState(608);
				match(RP);
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(613);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==STRING) {
				{
				setState(612);
				match(STRING);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Col_aliasContext extends ParserRuleContext {
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Col_aliasContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_col_alias; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCol_alias(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCol_alias(this);
		}
	}

	public final Col_aliasContext col_alias() throws RecognitionException {
		Col_aliasContext _localctx = new Col_aliasContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_col_alias);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(617);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(615);
				match(AS);
				setState(616);
				id();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Orderby_clauseContext extends ParserRuleContext {
		public TerminalNode ORDER() { return getToken(KVQLParser.ORDER, 0); }
		public TerminalNode BY() { return getToken(KVQLParser.BY, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<Sort_specContext> sort_spec() {
			return getRuleContexts(Sort_specContext.class);
		}
		public Sort_specContext sort_spec(int i) {
			return getRuleContext(Sort_specContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Orderby_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orderby_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterOrderby_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitOrderby_clause(this);
		}
	}

	public final Orderby_clauseContext orderby_clause() throws RecognitionException {
		Orderby_clauseContext _localctx = new Orderby_clauseContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_orderby_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(619);
			match(ORDER);
			setState(620);
			match(BY);
			setState(621);
			expr();
			setState(622);
			sort_spec();
			setState(629);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(623);
				match(COMMA);
				setState(624);
				expr();
				setState(625);
				sort_spec();
				}
				}
				setState(631);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sort_specContext extends ParserRuleContext {
		public TerminalNode NULLS() { return getToken(KVQLParser.NULLS, 0); }
		public TerminalNode ASC() { return getToken(KVQLParser.ASC, 0); }
		public TerminalNode DESC() { return getToken(KVQLParser.DESC, 0); }
		public TerminalNode FIRST() { return getToken(KVQLParser.FIRST, 0); }
		public TerminalNode LAST() { return getToken(KVQLParser.LAST, 0); }
		public Sort_specContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sort_spec; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSort_spec(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSort_spec(this);
		}
	}

	public final Sort_specContext sort_spec() throws RecognitionException {
		Sort_specContext _localctx = new Sort_specContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_sort_spec);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(633);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASC || _la==DESC) {
				{
				setState(632);
				_la = _input.LA(1);
				if ( !(_la==ASC || _la==DESC) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
			}

			setState(637);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NULLS) {
				{
				setState(635);
				match(NULLS);
				setState(636);
				_la = _input.LA(1);
				if ( !(_la==FIRST || _la==LAST) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Groupby_clauseContext extends ParserRuleContext {
		public TerminalNode GROUP() { return getToken(KVQLParser.GROUP, 0); }
		public TerminalNode BY() { return getToken(KVQLParser.BY, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Groupby_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_groupby_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterGroupby_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitGroupby_clause(this);
		}
	}

	public final Groupby_clauseContext groupby_clause() throws RecognitionException {
		Groupby_clauseContext _localctx = new Groupby_clauseContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_groupby_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(639);
			match(GROUP);
			setState(640);
			match(BY);
			setState(641);
			expr();
			setState(646);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(642);
				match(COMMA);
				setState(643);
				expr();
				}
				}
				setState(648);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Limit_clauseContext extends ParserRuleContext {
		public TerminalNode LIMIT() { return getToken(KVQLParser.LIMIT, 0); }
		public Add_exprContext add_expr() {
			return getRuleContext(Add_exprContext.class,0);
		}
		public Limit_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_limit_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterLimit_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitLimit_clause(this);
		}
	}

	public final Limit_clauseContext limit_clause() throws RecognitionException {
		Limit_clauseContext _localctx = new Limit_clauseContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_limit_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(649);
			match(LIMIT);
			setState(650);
			add_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Offset_clauseContext extends ParserRuleContext {
		public TerminalNode OFFSET() { return getToken(KVQLParser.OFFSET, 0); }
		public Add_exprContext add_expr() {
			return getRuleContext(Add_exprContext.class,0);
		}
		public Offset_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_offset_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterOffset_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitOffset_clause(this);
		}
	}

	public final Offset_clauseContext offset_clause() throws RecognitionException {
		Offset_clauseContext _localctx = new Offset_clauseContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_offset_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(652);
			match(OFFSET);
			setState(653);
			add_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Or_exprContext extends ParserRuleContext {
		public And_exprContext and_expr() {
			return getRuleContext(And_exprContext.class,0);
		}
		public Or_exprContext or_expr() {
			return getRuleContext(Or_exprContext.class,0);
		}
		public TerminalNode OR() { return getToken(KVQLParser.OR, 0); }
		public Or_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_or_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterOr_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitOr_expr(this);
		}
	}

	public final Or_exprContext or_expr() throws RecognitionException {
		return or_expr(0);
	}

	private Or_exprContext or_expr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		Or_exprContext _localctx = new Or_exprContext(_ctx, _parentState);
		Or_exprContext _prevctx = _localctx;
		int _startState = 54;
		enterRecursionRule(_localctx, 54, RULE_or_expr, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(656);
			and_expr(0);
			}
			_ctx.stop = _input.LT(-1);
			setState(663);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,33,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new Or_exprContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_or_expr);
					setState(658);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(659);
					match(OR);
					setState(660);
					and_expr(0);
					}
					} 
				}
				setState(665);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,33,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class And_exprContext extends ParserRuleContext {
		public Not_exprContext not_expr() {
			return getRuleContext(Not_exprContext.class,0);
		}
		public And_exprContext and_expr() {
			return getRuleContext(And_exprContext.class,0);
		}
		public TerminalNode AND() { return getToken(KVQLParser.AND, 0); }
		public And_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_and_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAnd_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAnd_expr(this);
		}
	}

	public final And_exprContext and_expr() throws RecognitionException {
		return and_expr(0);
	}

	private And_exprContext and_expr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		And_exprContext _localctx = new And_exprContext(_ctx, _parentState);
		And_exprContext _prevctx = _localctx;
		int _startState = 56;
		enterRecursionRule(_localctx, 56, RULE_and_expr, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(667);
			not_expr();
			}
			_ctx.stop = _input.LT(-1);
			setState(674);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,34,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new And_exprContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_and_expr);
					setState(669);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(670);
					match(AND);
					setState(671);
					not_expr();
					}
					} 
				}
				setState(676);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,34,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class Not_exprContext extends ParserRuleContext {
		public Is_null_exprContext is_null_expr() {
			return getRuleContext(Is_null_exprContext.class,0);
		}
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public Not_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_not_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterNot_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitNot_expr(this);
		}
	}

	public final Not_exprContext not_expr() throws RecognitionException {
		Not_exprContext _localctx = new Not_exprContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_not_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(678);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
			case 1:
				{
				setState(677);
				match(NOT);
				}
				break;
			}
			setState(680);
			is_null_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Is_null_exprContext extends ParserRuleContext {
		public Cond_exprContext cond_expr() {
			return getRuleContext(Cond_exprContext.class,0);
		}
		public TerminalNode IS() { return getToken(KVQLParser.IS, 0); }
		public TerminalNode NULL() { return getToken(KVQLParser.NULL, 0); }
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public Is_null_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_is_null_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIs_null_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIs_null_expr(this);
		}
	}

	public final Is_null_exprContext is_null_expr() throws RecognitionException {
		Is_null_exprContext _localctx = new Is_null_exprContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_is_null_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(682);
			cond_expr();
			setState(688);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,37,_ctx) ) {
			case 1:
				{
				setState(683);
				match(IS);
				setState(685);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(684);
					match(NOT);
					}
				}

				setState(687);
				match(NULL);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Cond_exprContext extends ParserRuleContext {
		public Comp_exprContext comp_expr() {
			return getRuleContext(Comp_exprContext.class,0);
		}
		public In_exprContext in_expr() {
			return getRuleContext(In_exprContext.class,0);
		}
		public Exists_exprContext exists_expr() {
			return getRuleContext(Exists_exprContext.class,0);
		}
		public Is_of_type_exprContext is_of_type_expr() {
			return getRuleContext(Is_of_type_exprContext.class,0);
		}
		public Cond_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_cond_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCond_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCond_expr(this);
		}
	}

	public final Cond_exprContext cond_expr() throws RecognitionException {
		Cond_exprContext _localctx = new Cond_exprContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_cond_expr);
		try {
			setState(694);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,38,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(690);
				comp_expr();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(691);
				in_expr();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(692);
				exists_expr();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(693);
				is_of_type_expr();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Comp_exprContext extends ParserRuleContext {
		public List<Concatenate_exprContext> concatenate_expr() {
			return getRuleContexts(Concatenate_exprContext.class);
		}
		public Concatenate_exprContext concatenate_expr(int i) {
			return getRuleContext(Concatenate_exprContext.class,i);
		}
		public Comp_opContext comp_op() {
			return getRuleContext(Comp_opContext.class,0);
		}
		public Any_opContext any_op() {
			return getRuleContext(Any_opContext.class,0);
		}
		public Comp_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comp_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterComp_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitComp_expr(this);
		}
	}

	public final Comp_exprContext comp_expr() throws RecognitionException {
		Comp_exprContext _localctx = new Comp_exprContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_comp_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(696);
			concatenate_expr();
			setState(703);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,40,_ctx) ) {
			case 1:
				{
				setState(699);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case LT:
				case LTE:
				case GT:
				case GTE:
				case EQ:
				case NEQ:
					{
					setState(697);
					comp_op();
					}
					break;
				case LT_ANY:
				case LTE_ANY:
				case GT_ANY:
				case GTE_ANY:
				case EQ_ANY:
				case NEQ_ANY:
					{
					setState(698);
					any_op();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(701);
				concatenate_expr();
				}
				break;
			}
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Comp_opContext extends ParserRuleContext {
		public TerminalNode EQ() { return getToken(KVQLParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(KVQLParser.NEQ, 0); }
		public TerminalNode GT() { return getToken(KVQLParser.GT, 0); }
		public TerminalNode GTE() { return getToken(KVQLParser.GTE, 0); }
		public TerminalNode LT() { return getToken(KVQLParser.LT, 0); }
		public TerminalNode LTE() { return getToken(KVQLParser.LTE, 0); }
		public Comp_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comp_op; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterComp_op(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitComp_op(this);
		}
	}

	public final Comp_opContext comp_op() throws RecognitionException {
		Comp_opContext _localctx = new Comp_opContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_comp_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(705);
			_la = _input.LA(1);
			if ( !(((((_la - 162)) & ~0x3f) == 0 && ((1L << (_la - 162)) & ((1L << (LT - 162)) | (1L << (LTE - 162)) | (1L << (GT - 162)) | (1L << (GTE - 162)) | (1L << (EQ - 162)) | (1L << (NEQ - 162)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Any_opContext extends ParserRuleContext {
		public TerminalNode EQ_ANY() { return getToken(KVQLParser.EQ_ANY, 0); }
		public TerminalNode NEQ_ANY() { return getToken(KVQLParser.NEQ_ANY, 0); }
		public TerminalNode GT_ANY() { return getToken(KVQLParser.GT_ANY, 0); }
		public TerminalNode GTE_ANY() { return getToken(KVQLParser.GTE_ANY, 0); }
		public TerminalNode LT_ANY() { return getToken(KVQLParser.LT_ANY, 0); }
		public TerminalNode LTE_ANY() { return getToken(KVQLParser.LTE_ANY, 0); }
		public Any_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_any_op; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAny_op(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAny_op(this);
		}
	}

	public final Any_opContext any_op() throws RecognitionException {
		Any_opContext _localctx = new Any_opContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_any_op);
		try {
			setState(713);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EQ_ANY:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(707);
				match(EQ_ANY);
				}
				}
				break;
			case NEQ_ANY:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(708);
				match(NEQ_ANY);
				}
				}
				break;
			case GT_ANY:
				enterOuterAlt(_localctx, 3);
				{
				{
				setState(709);
				match(GT_ANY);
				}
				}
				break;
			case GTE_ANY:
				enterOuterAlt(_localctx, 4);
				{
				{
				setState(710);
				match(GTE_ANY);
				}
				}
				break;
			case LT_ANY:
				enterOuterAlt(_localctx, 5);
				{
				{
				setState(711);
				match(LT_ANY);
				}
				}
				break;
			case LTE_ANY:
				enterOuterAlt(_localctx, 6);
				{
				{
				setState(712);
				match(LTE_ANY);
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class In_exprContext extends ParserRuleContext {
		public In1_exprContext in1_expr() {
			return getRuleContext(In1_exprContext.class,0);
		}
		public In2_exprContext in2_expr() {
			return getRuleContext(In2_exprContext.class,0);
		}
		public In3_exprContext in3_expr() {
			return getRuleContext(In3_exprContext.class,0);
		}
		public In_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIn_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIn_expr(this);
		}
	}

	public final In_exprContext in_expr() throws RecognitionException {
		In_exprContext _localctx = new In_exprContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_in_expr);
		try {
			setState(718);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,42,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(715);
				in1_expr();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(716);
				in2_expr();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(717);
				in3_expr();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class In1_exprContext extends ParserRuleContext {
		public In1_left_opContext in1_left_op() {
			return getRuleContext(In1_left_opContext.class,0);
		}
		public TerminalNode IN() { return getToken(KVQLParser.IN, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public List<In1_expr_listContext> in1_expr_list() {
			return getRuleContexts(In1_expr_listContext.class);
		}
		public In1_expr_listContext in1_expr_list(int i) {
			return getRuleContext(In1_expr_listContext.class,i);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public In1_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in1_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIn1_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIn1_expr(this);
		}
	}

	public final In1_exprContext in1_expr() throws RecognitionException {
		In1_exprContext _localctx = new In1_exprContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_in1_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(720);
			in1_left_op();
			setState(721);
			match(IN);
			setState(722);
			match(LP);
			setState(723);
			in1_expr_list();
			setState(726); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(724);
				match(COMMA);
				setState(725);
				in1_expr_list();
				}
				}
				setState(728); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==COMMA );
			setState(730);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class In1_left_opContext extends ParserRuleContext {
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public List<Concatenate_exprContext> concatenate_expr() {
			return getRuleContexts(Concatenate_exprContext.class);
		}
		public Concatenate_exprContext concatenate_expr(int i) {
			return getRuleContext(Concatenate_exprContext.class,i);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public In1_left_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in1_left_op; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIn1_left_op(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIn1_left_op(this);
		}
	}

	public final In1_left_opContext in1_left_op() throws RecognitionException {
		In1_left_opContext _localctx = new In1_left_opContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_in1_left_op);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(732);
			match(LP);
			setState(733);
			concatenate_expr();
			setState(738);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(734);
				match(COMMA);
				setState(735);
				concatenate_expr();
				}
				}
				setState(740);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(741);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class In1_expr_listContext extends ParserRuleContext {
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public In1_expr_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in1_expr_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIn1_expr_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIn1_expr_list(this);
		}
	}

	public final In1_expr_listContext in1_expr_list() throws RecognitionException {
		In1_expr_listContext _localctx = new In1_expr_listContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_in1_expr_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(743);
			match(LP);
			setState(744);
			expr();
			setState(749);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(745);
				match(COMMA);
				setState(746);
				expr();
				}
				}
				setState(751);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(752);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class In2_exprContext extends ParserRuleContext {
		public Concatenate_exprContext concatenate_expr() {
			return getRuleContext(Concatenate_exprContext.class,0);
		}
		public TerminalNode IN() { return getToken(KVQLParser.IN, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public In2_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in2_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIn2_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIn2_expr(this);
		}
	}

	public final In2_exprContext in2_expr() throws RecognitionException {
		In2_exprContext _localctx = new In2_exprContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_in2_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(754);
			concatenate_expr();
			setState(755);
			match(IN);
			setState(756);
			match(LP);
			setState(757);
			expr();
			setState(760); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(758);
				match(COMMA);
				setState(759);
				expr();
				}
				}
				setState(762); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==COMMA );
			setState(764);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class In3_exprContext extends ParserRuleContext {
		public TerminalNode IN() { return getToken(KVQLParser.IN, 0); }
		public Path_exprContext path_expr() {
			return getRuleContext(Path_exprContext.class,0);
		}
		public List<Concatenate_exprContext> concatenate_expr() {
			return getRuleContexts(Concatenate_exprContext.class);
		}
		public Concatenate_exprContext concatenate_expr(int i) {
			return getRuleContext(Concatenate_exprContext.class,i);
		}
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public In3_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in3_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIn3_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIn3_expr(this);
		}
	}

	public final In3_exprContext in3_expr() throws RecognitionException {
		In3_exprContext _localctx = new In3_exprContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_in3_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(778);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,48,_ctx) ) {
			case 1:
				{
				setState(766);
				concatenate_expr();
				}
				break;
			case 2:
				{
				{
				setState(767);
				match(LP);
				setState(768);
				concatenate_expr();
				setState(773);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(769);
					match(COMMA);
					setState(770);
					concatenate_expr();
					}
					}
					setState(775);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(776);
				match(RP);
				}
				}
				break;
			}
			setState(780);
			match(IN);
			setState(781);
			path_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Exists_exprContext extends ParserRuleContext {
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public Concatenate_exprContext concatenate_expr() {
			return getRuleContext(Concatenate_exprContext.class,0);
		}
		public Exists_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exists_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterExists_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitExists_expr(this);
		}
	}

	public final Exists_exprContext exists_expr() throws RecognitionException {
		Exists_exprContext _localctx = new Exists_exprContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_exists_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(783);
			match(EXISTS);
			setState(784);
			concatenate_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Is_of_type_exprContext extends ParserRuleContext {
		public Concatenate_exprContext concatenate_expr() {
			return getRuleContext(Concatenate_exprContext.class,0);
		}
		public TerminalNode IS() { return getToken(KVQLParser.IS, 0); }
		public TerminalNode OF() { return getToken(KVQLParser.OF, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public List<Quantified_type_defContext> quantified_type_def() {
			return getRuleContexts(Quantified_type_defContext.class);
		}
		public Quantified_type_defContext quantified_type_def(int i) {
			return getRuleContext(Quantified_type_defContext.class,i);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public TerminalNode TYPE() { return getToken(KVQLParser.TYPE, 0); }
		public List<TerminalNode> ONLY() { return getTokens(KVQLParser.ONLY); }
		public TerminalNode ONLY(int i) {
			return getToken(KVQLParser.ONLY, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Is_of_type_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_is_of_type_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIs_of_type_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIs_of_type_expr(this);
		}
	}

	public final Is_of_type_exprContext is_of_type_expr() throws RecognitionException {
		Is_of_type_exprContext _localctx = new Is_of_type_exprContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_is_of_type_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(786);
			concatenate_expr();
			setState(787);
			match(IS);
			setState(789);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(788);
				match(NOT);
				}
			}

			setState(791);
			match(OF);
			setState(793);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==TYPE) {
				{
				setState(792);
				match(TYPE);
				}
			}

			setState(795);
			match(LP);
			setState(797);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ONLY) {
				{
				setState(796);
				match(ONLY);
				}
			}

			setState(799);
			quantified_type_def();
			setState(807);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(800);
				match(COMMA);
				setState(802);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ONLY) {
					{
					setState(801);
					match(ONLY);
					}
				}

				setState(804);
				quantified_type_def();
				}
				}
				setState(809);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(810);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Concatenate_exprContext extends ParserRuleContext {
		public List<Add_exprContext> add_expr() {
			return getRuleContexts(Add_exprContext.class);
		}
		public Add_exprContext add_expr(int i) {
			return getRuleContext(Add_exprContext.class,i);
		}
		public List<TerminalNode> CONCAT() { return getTokens(KVQLParser.CONCAT); }
		public TerminalNode CONCAT(int i) {
			return getToken(KVQLParser.CONCAT, i);
		}
		public Concatenate_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_concatenate_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterConcatenate_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitConcatenate_expr(this);
		}
	}

	public final Concatenate_exprContext concatenate_expr() throws RecognitionException {
		Concatenate_exprContext _localctx = new Concatenate_exprContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_concatenate_expr);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(812);
			add_expr();
			setState(817);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(813);
					match(CONCAT);
					setState(814);
					add_expr();
					}
					} 
				}
				setState(819);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Add_exprContext extends ParserRuleContext {
		public List<Multiply_exprContext> multiply_expr() {
			return getRuleContexts(Multiply_exprContext.class);
		}
		public Multiply_exprContext multiply_expr(int i) {
			return getRuleContext(Multiply_exprContext.class,i);
		}
		public List<TerminalNode> PLUS() { return getTokens(KVQLParser.PLUS); }
		public TerminalNode PLUS(int i) {
			return getToken(KVQLParser.PLUS, i);
		}
		public List<TerminalNode> MINUS() { return getTokens(KVQLParser.MINUS); }
		public TerminalNode MINUS(int i) {
			return getToken(KVQLParser.MINUS, i);
		}
		public Add_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_add_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAdd_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAdd_expr(this);
		}
	}

	public final Add_exprContext add_expr() throws RecognitionException {
		Add_exprContext _localctx = new Add_exprContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_add_expr);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(820);
			multiply_expr();
			setState(825);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(821);
					_la = _input.LA(1);
					if ( !(_la==PLUS || _la==MINUS) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(822);
					multiply_expr();
					}
					} 
				}
				setState(827);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Multiply_exprContext extends ParserRuleContext {
		public List<Unary_exprContext> unary_expr() {
			return getRuleContexts(Unary_exprContext.class);
		}
		public Unary_exprContext unary_expr(int i) {
			return getRuleContext(Unary_exprContext.class,i);
		}
		public List<TerminalNode> STAR() { return getTokens(KVQLParser.STAR); }
		public TerminalNode STAR(int i) {
			return getToken(KVQLParser.STAR, i);
		}
		public List<TerminalNode> IDIV() { return getTokens(KVQLParser.IDIV); }
		public TerminalNode IDIV(int i) {
			return getToken(KVQLParser.IDIV, i);
		}
		public List<TerminalNode> RDIV() { return getTokens(KVQLParser.RDIV); }
		public TerminalNode RDIV(int i) {
			return getToken(KVQLParser.RDIV, i);
		}
		public Multiply_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_multiply_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterMultiply_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitMultiply_expr(this);
		}
	}

	public final Multiply_exprContext multiply_expr() throws RecognitionException {
		Multiply_exprContext _localctx = new Multiply_exprContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_multiply_expr);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(828);
			unary_expr();
			setState(833);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(829);
					_la = _input.LA(1);
					if ( !(((((_la - 158)) & ~0x3f) == 0 && ((1L << (_la - 158)) & ((1L << (STAR - 158)) | (1L << (IDIV - 158)) | (1L << (RDIV - 158)))) != 0)) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(830);
					unary_expr();
					}
					} 
				}
				setState(835);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unary_exprContext extends ParserRuleContext {
		public Path_exprContext path_expr() {
			return getRuleContext(Path_exprContext.class,0);
		}
		public Unary_exprContext unary_expr() {
			return getRuleContext(Unary_exprContext.class,0);
		}
		public TerminalNode PLUS() { return getToken(KVQLParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(KVQLParser.MINUS, 0); }
		public Unary_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterUnary_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitUnary_expr(this);
		}
	}

	public final Unary_exprContext unary_expr() throws RecognitionException {
		Unary_exprContext _localctx = new Unary_exprContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_unary_expr);
		int _la;
		try {
			setState(839);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,57,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(836);
				path_expr();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(837);
				_la = _input.LA(1);
				if ( !(_la==PLUS || _la==MINUS) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(838);
				unary_expr();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Path_exprContext extends ParserRuleContext {
		public Primary_exprContext primary_expr() {
			return getRuleContext(Primary_exprContext.class,0);
		}
		public List<Map_stepContext> map_step() {
			return getRuleContexts(Map_stepContext.class);
		}
		public Map_stepContext map_step(int i) {
			return getRuleContext(Map_stepContext.class,i);
		}
		public List<Array_stepContext> array_step() {
			return getRuleContexts(Array_stepContext.class);
		}
		public Array_stepContext array_step(int i) {
			return getRuleContext(Array_stepContext.class,i);
		}
		public Path_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_path_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPath_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPath_expr(this);
		}
	}

	public final Path_exprContext path_expr() throws RecognitionException {
		Path_exprContext _localctx = new Path_exprContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_path_expr);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(841);
			primary_expr();
			setState(846);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,59,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					setState(844);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case DOT:
						{
						setState(842);
						map_step();
						}
						break;
					case LBRACK:
						{
						setState(843);
						array_step();
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					} 
				}
				setState(848);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,59,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Map_stepContext extends ParserRuleContext {
		public TerminalNode DOT() { return getToken(KVQLParser.DOT, 0); }
		public Map_filter_stepContext map_filter_step() {
			return getRuleContext(Map_filter_stepContext.class,0);
		}
		public Map_field_stepContext map_field_step() {
			return getRuleContext(Map_field_stepContext.class,0);
		}
		public Map_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_map_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterMap_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitMap_step(this);
		}
	}

	public final Map_stepContext map_step() throws RecognitionException {
		Map_stepContext _localctx = new Map_stepContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_map_step);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(849);
			match(DOT);
			setState(852);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,60,_ctx) ) {
			case 1:
				{
				setState(850);
				map_filter_step();
				}
				break;
			case 2:
				{
				setState(851);
				map_field_step();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Map_field_stepContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public Var_refContext var_ref() {
			return getRuleContext(Var_refContext.class,0);
		}
		public Parenthesized_exprContext parenthesized_expr() {
			return getRuleContext(Parenthesized_exprContext.class,0);
		}
		public Func_callContext func_call() {
			return getRuleContext(Func_callContext.class,0);
		}
		public Map_field_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_map_field_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterMap_field_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitMap_field_step(this);
		}
	}

	public final Map_field_stepContext map_field_step() throws RecognitionException {
		Map_field_stepContext _localctx = new Map_field_stepContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_map_field_step);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(859);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,61,_ctx) ) {
			case 1:
				{
				setState(854);
				id();
				}
				break;
			case 2:
				{
				setState(855);
				string();
				}
				break;
			case 3:
				{
				setState(856);
				var_ref();
				}
				break;
			case 4:
				{
				setState(857);
				parenthesized_expr();
				}
				break;
			case 5:
				{
				setState(858);
				func_call();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Map_filter_stepContext extends ParserRuleContext {
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public TerminalNode KEYS() { return getToken(KVQLParser.KEYS, 0); }
		public TerminalNode VALUES() { return getToken(KVQLParser.VALUES, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Map_filter_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_map_filter_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterMap_filter_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitMap_filter_step(this);
		}
	}

	public final Map_filter_stepContext map_filter_step() throws RecognitionException {
		Map_filter_stepContext _localctx = new Map_filter_stepContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_map_filter_step);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(861);
			_la = _input.LA(1);
			if ( !(_la==KEYS || _la==VALUES) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(862);
			match(LP);
			setState(864);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << VARNAME) | (1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (LP - 129)) | (1L << (LBRACK - 129)) | (1L << (LBRACE - 129)) | (1L << (DOLLAR - 129)) | (1L << (QUESTION_MARK - 129)) | (1L << (PLUS - 129)) | (1L << (MINUS - 129)) | (1L << (RDIV - 129)) | (1L << (NULL - 129)) | (1L << (FALSE - 129)) | (1L << (TRUE - 129)) | (1L << (INT - 129)) | (1L << (FLOAT - 129)) | (1L << (NUMBER - 129)) | (1L << (DSTRING - 129)) | (1L << (STRING - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
				{
				setState(863);
				expr();
				}
			}

			setState(866);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Array_stepContext extends ParserRuleContext {
		public Array_filter_stepContext array_filter_step() {
			return getRuleContext(Array_filter_stepContext.class,0);
		}
		public Array_slice_stepContext array_slice_step() {
			return getRuleContext(Array_slice_stepContext.class,0);
		}
		public Array_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterArray_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitArray_step(this);
		}
	}

	public final Array_stepContext array_step() throws RecognitionException {
		Array_stepContext _localctx = new Array_stepContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_array_step);
		try {
			setState(870);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,63,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(868);
				array_filter_step();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(869);
				array_slice_step();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Array_slice_stepContext extends ParserRuleContext {
		public TerminalNode LBRACK() { return getToken(KVQLParser.LBRACK, 0); }
		public TerminalNode COLON() { return getToken(KVQLParser.COLON, 0); }
		public TerminalNode RBRACK() { return getToken(KVQLParser.RBRACK, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public Array_slice_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array_slice_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterArray_slice_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitArray_slice_step(this);
		}
	}

	public final Array_slice_stepContext array_slice_step() throws RecognitionException {
		Array_slice_stepContext _localctx = new Array_slice_stepContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_array_slice_step);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(872);
			match(LBRACK);
			setState(874);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << VARNAME) | (1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (LP - 129)) | (1L << (LBRACK - 129)) | (1L << (LBRACE - 129)) | (1L << (DOLLAR - 129)) | (1L << (QUESTION_MARK - 129)) | (1L << (PLUS - 129)) | (1L << (MINUS - 129)) | (1L << (RDIV - 129)) | (1L << (NULL - 129)) | (1L << (FALSE - 129)) | (1L << (TRUE - 129)) | (1L << (INT - 129)) | (1L << (FLOAT - 129)) | (1L << (NUMBER - 129)) | (1L << (DSTRING - 129)) | (1L << (STRING - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
				{
				setState(873);
				expr();
				}
			}

			setState(876);
			match(COLON);
			setState(878);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << VARNAME) | (1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (LP - 129)) | (1L << (LBRACK - 129)) | (1L << (LBRACE - 129)) | (1L << (DOLLAR - 129)) | (1L << (QUESTION_MARK - 129)) | (1L << (PLUS - 129)) | (1L << (MINUS - 129)) | (1L << (RDIV - 129)) | (1L << (NULL - 129)) | (1L << (FALSE - 129)) | (1L << (TRUE - 129)) | (1L << (INT - 129)) | (1L << (FLOAT - 129)) | (1L << (NUMBER - 129)) | (1L << (DSTRING - 129)) | (1L << (STRING - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
				{
				setState(877);
				expr();
				}
			}

			setState(880);
			match(RBRACK);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Array_filter_stepContext extends ParserRuleContext {
		public TerminalNode LBRACK() { return getToken(KVQLParser.LBRACK, 0); }
		public TerminalNode RBRACK() { return getToken(KVQLParser.RBRACK, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Array_filter_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array_filter_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterArray_filter_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitArray_filter_step(this);
		}
	}

	public final Array_filter_stepContext array_filter_step() throws RecognitionException {
		Array_filter_stepContext _localctx = new Array_filter_stepContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_array_filter_step);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(882);
			match(LBRACK);
			setState(884);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << VARNAME) | (1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (LP - 129)) | (1L << (LBRACK - 129)) | (1L << (LBRACE - 129)) | (1L << (DOLLAR - 129)) | (1L << (QUESTION_MARK - 129)) | (1L << (PLUS - 129)) | (1L << (MINUS - 129)) | (1L << (RDIV - 129)) | (1L << (NULL - 129)) | (1L << (FALSE - 129)) | (1L << (TRUE - 129)) | (1L << (INT - 129)) | (1L << (FLOAT - 129)) | (1L << (NUMBER - 129)) | (1L << (DSTRING - 129)) | (1L << (STRING - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
				{
				setState(883);
				expr();
				}
			}

			setState(886);
			match(RBRACK);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Primary_exprContext extends ParserRuleContext {
		public Const_exprContext const_expr() {
			return getRuleContext(Const_exprContext.class,0);
		}
		public Column_refContext column_ref() {
			return getRuleContext(Column_refContext.class,0);
		}
		public Var_refContext var_ref() {
			return getRuleContext(Var_refContext.class,0);
		}
		public Array_constructorContext array_constructor() {
			return getRuleContext(Array_constructorContext.class,0);
		}
		public Map_constructorContext map_constructor() {
			return getRuleContext(Map_constructorContext.class,0);
		}
		public Transform_exprContext transform_expr() {
			return getRuleContext(Transform_exprContext.class,0);
		}
		public Func_callContext func_call() {
			return getRuleContext(Func_callContext.class,0);
		}
		public Count_starContext count_star() {
			return getRuleContext(Count_starContext.class,0);
		}
		public Case_exprContext case_expr() {
			return getRuleContext(Case_exprContext.class,0);
		}
		public Cast_exprContext cast_expr() {
			return getRuleContext(Cast_exprContext.class,0);
		}
		public Parenthesized_exprContext parenthesized_expr() {
			return getRuleContext(Parenthesized_exprContext.class,0);
		}
		public Extract_exprContext extract_expr() {
			return getRuleContext(Extract_exprContext.class,0);
		}
		public Primary_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primary_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPrimary_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPrimary_expr(this);
		}
	}

	public final Primary_exprContext primary_expr() throws RecognitionException {
		Primary_exprContext _localctx = new Primary_exprContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_primary_expr);
		try {
			setState(900);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,67,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(888);
				const_expr();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(889);
				column_ref();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(890);
				var_ref();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(891);
				array_constructor();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(892);
				map_constructor();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(893);
				transform_expr();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(894);
				func_call();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(895);
				count_star();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(896);
				case_expr();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(897);
				cast_expr();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(898);
				parenthesized_expr();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(899);
				extract_expr();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Column_refContext extends ParserRuleContext {
		public List<IdContext> id() {
			return getRuleContexts(IdContext.class);
		}
		public IdContext id(int i) {
			return getRuleContext(IdContext.class,i);
		}
		public TerminalNode DOT() { return getToken(KVQLParser.DOT, 0); }
		public Column_refContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_column_ref; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterColumn_ref(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitColumn_ref(this);
		}
	}

	public final Column_refContext column_ref() throws RecognitionException {
		Column_refContext _localctx = new Column_refContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_column_ref);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(902);
			id();
			setState(905);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,68,_ctx) ) {
			case 1:
				{
				setState(903);
				match(DOT);
				setState(904);
				id();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Const_exprContext extends ParserRuleContext {
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public TerminalNode TRUE() { return getToken(KVQLParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(KVQLParser.FALSE, 0); }
		public TerminalNode NULL() { return getToken(KVQLParser.NULL, 0); }
		public Const_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_const_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterConst_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitConst_expr(this);
		}
	}

	public final Const_exprContext const_expr() throws RecognitionException {
		Const_exprContext _localctx = new Const_exprContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_const_expr);
		try {
			setState(912);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MINUS:
			case INT:
			case FLOAT:
			case NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(907);
				number();
				}
				break;
			case DSTRING:
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(908);
				string();
				}
				break;
			case TRUE:
				enterOuterAlt(_localctx, 3);
				{
				setState(909);
				match(TRUE);
				}
				break;
			case FALSE:
				enterOuterAlt(_localctx, 4);
				{
				setState(910);
				match(FALSE);
				}
				break;
			case NULL:
				enterOuterAlt(_localctx, 5);
				{
				setState(911);
				match(NULL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Var_refContext extends ParserRuleContext {
		public TerminalNode VARNAME() { return getToken(KVQLParser.VARNAME, 0); }
		public TerminalNode DOLLAR() { return getToken(KVQLParser.DOLLAR, 0); }
		public TerminalNode QUESTION_MARK() { return getToken(KVQLParser.QUESTION_MARK, 0); }
		public Var_refContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_var_ref; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterVar_ref(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitVar_ref(this);
		}
	}

	public final Var_refContext var_ref() throws RecognitionException {
		Var_refContext _localctx = new Var_refContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_var_ref);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(914);
			_la = _input.LA(1);
			if ( !(_la==VARNAME || _la==DOLLAR || _la==QUESTION_MARK) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Array_constructorContext extends ParserRuleContext {
		public TerminalNode LBRACK() { return getToken(KVQLParser.LBRACK, 0); }
		public TerminalNode RBRACK() { return getToken(KVQLParser.RBRACK, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Array_constructorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array_constructor; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterArray_constructor(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitArray_constructor(this);
		}
	}

	public final Array_constructorContext array_constructor() throws RecognitionException {
		Array_constructorContext _localctx = new Array_constructorContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_array_constructor);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(916);
			match(LBRACK);
			setState(918);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << VARNAME) | (1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (LP - 129)) | (1L << (LBRACK - 129)) | (1L << (LBRACE - 129)) | (1L << (DOLLAR - 129)) | (1L << (QUESTION_MARK - 129)) | (1L << (PLUS - 129)) | (1L << (MINUS - 129)) | (1L << (RDIV - 129)) | (1L << (NULL - 129)) | (1L << (FALSE - 129)) | (1L << (TRUE - 129)) | (1L << (INT - 129)) | (1L << (FLOAT - 129)) | (1L << (NUMBER - 129)) | (1L << (DSTRING - 129)) | (1L << (STRING - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
				{
				setState(917);
				expr();
				}
			}

			setState(924);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(920);
				match(COMMA);
				setState(921);
				expr();
				}
				}
				setState(926);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(927);
			match(RBRACK);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Map_constructorContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(KVQLParser.LBRACE, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> COLON() { return getTokens(KVQLParser.COLON); }
		public TerminalNode COLON(int i) {
			return getToken(KVQLParser.COLON, i);
		}
		public TerminalNode RBRACE() { return getToken(KVQLParser.RBRACE, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Map_constructorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_map_constructor; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterMap_constructor(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitMap_constructor(this);
		}
	}

	public final Map_constructorContext map_constructor() throws RecognitionException {
		Map_constructorContext _localctx = new Map_constructorContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_map_constructor);
		int _la;
		try {
			setState(947);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,73,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(929);
				match(LBRACE);
				setState(930);
				expr();
				setState(931);
				match(COLON);
				setState(932);
				expr();
				setState(940);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(933);
					match(COMMA);
					setState(934);
					expr();
					setState(935);
					match(COLON);
					setState(936);
					expr();
					}
					}
					setState(942);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(943);
				match(RBRACE);
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(945);
				match(LBRACE);
				setState(946);
				match(RBRACE);
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Transform_exprContext extends ParserRuleContext {
		public TerminalNode SEQ_TRANSFORM() { return getToken(KVQLParser.SEQ_TRANSFORM, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Transform_input_exprContext transform_input_expr() {
			return getRuleContext(Transform_input_exprContext.class,0);
		}
		public TerminalNode COMMA() { return getToken(KVQLParser.COMMA, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Transform_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_transform_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTransform_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTransform_expr(this);
		}
	}

	public final Transform_exprContext transform_expr() throws RecognitionException {
		Transform_exprContext _localctx = new Transform_exprContext(_ctx, getState());
		enterRule(_localctx, 120, RULE_transform_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(949);
			match(SEQ_TRANSFORM);
			setState(950);
			match(LP);
			setState(951);
			transform_input_expr();
			setState(952);
			match(COMMA);
			setState(953);
			expr();
			setState(954);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Transform_input_exprContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Transform_input_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_transform_input_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTransform_input_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTransform_input_expr(this);
		}
	}

	public final Transform_input_exprContext transform_input_expr() throws RecognitionException {
		Transform_input_exprContext _localctx = new Transform_input_exprContext(_ctx, getState());
		enterRule(_localctx, 122, RULE_transform_input_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(956);
			expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Func_callContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Func_callContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_func_call; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFunc_call(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFunc_call(this);
		}
	}

	public final Func_callContext func_call() throws RecognitionException {
		Func_callContext _localctx = new Func_callContext(_ctx, getState());
		enterRule(_localctx, 124, RULE_func_call);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(958);
			id();
			setState(959);
			match(LP);
			setState(968);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << VARNAME) | (1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (LP - 129)) | (1L << (LBRACK - 129)) | (1L << (LBRACE - 129)) | (1L << (DOLLAR - 129)) | (1L << (QUESTION_MARK - 129)) | (1L << (PLUS - 129)) | (1L << (MINUS - 129)) | (1L << (RDIV - 129)) | (1L << (NULL - 129)) | (1L << (FALSE - 129)) | (1L << (TRUE - 129)) | (1L << (INT - 129)) | (1L << (FLOAT - 129)) | (1L << (NUMBER - 129)) | (1L << (DSTRING - 129)) | (1L << (STRING - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
				{
				setState(960);
				expr();
				setState(965);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(961);
					match(COMMA);
					setState(962);
					expr();
					}
					}
					setState(967);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(970);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Count_starContext extends ParserRuleContext {
		public TerminalNode COUNT() { return getToken(KVQLParser.COUNT, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode STAR() { return getToken(KVQLParser.STAR, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Count_starContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_count_star; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCount_star(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCount_star(this);
		}
	}

	public final Count_starContext count_star() throws RecognitionException {
		Count_starContext _localctx = new Count_starContext(_ctx, getState());
		enterRule(_localctx, 126, RULE_count_star);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(972);
			match(COUNT);
			setState(973);
			match(LP);
			setState(974);
			match(STAR);
			setState(975);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Case_exprContext extends ParserRuleContext {
		public TerminalNode CASE() { return getToken(KVQLParser.CASE, 0); }
		public List<TerminalNode> WHEN() { return getTokens(KVQLParser.WHEN); }
		public TerminalNode WHEN(int i) {
			return getToken(KVQLParser.WHEN, i);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> THEN() { return getTokens(KVQLParser.THEN); }
		public TerminalNode THEN(int i) {
			return getToken(KVQLParser.THEN, i);
		}
		public TerminalNode END() { return getToken(KVQLParser.END, 0); }
		public TerminalNode ELSE() { return getToken(KVQLParser.ELSE, 0); }
		public Case_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_case_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCase_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCase_expr(this);
		}
	}

	public final Case_exprContext case_expr() throws RecognitionException {
		Case_exprContext _localctx = new Case_exprContext(_ctx, getState());
		enterRule(_localctx, 128, RULE_case_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(977);
			match(CASE);
			setState(978);
			match(WHEN);
			setState(979);
			expr();
			setState(980);
			match(THEN);
			setState(981);
			expr();
			setState(989);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==WHEN) {
				{
				{
				setState(982);
				match(WHEN);
				setState(983);
				expr();
				setState(984);
				match(THEN);
				setState(985);
				expr();
				}
				}
				setState(991);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(994);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(992);
				match(ELSE);
				setState(993);
				expr();
				}
			}

			setState(996);
			match(END);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Cast_exprContext extends ParserRuleContext {
		public TerminalNode CAST() { return getToken(KVQLParser.CAST, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public Quantified_type_defContext quantified_type_def() {
			return getRuleContext(Quantified_type_defContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Cast_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_cast_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCast_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCast_expr(this);
		}
	}

	public final Cast_exprContext cast_expr() throws RecognitionException {
		Cast_exprContext _localctx = new Cast_exprContext(_ctx, getState());
		enterRule(_localctx, 130, RULE_cast_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(998);
			match(CAST);
			setState(999);
			match(LP);
			setState(1000);
			expr();
			setState(1001);
			match(AS);
			setState(1002);
			quantified_type_def();
			setState(1003);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Parenthesized_exprContext extends ParserRuleContext {
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Parenthesized_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parenthesized_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterParenthesized_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitParenthesized_expr(this);
		}
	}

	public final Parenthesized_exprContext parenthesized_expr() throws RecognitionException {
		Parenthesized_exprContext _localctx = new Parenthesized_exprContext(_ctx, getState());
		enterRule(_localctx, 132, RULE_parenthesized_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1005);
			match(LP);
			setState(1006);
			expr();
			setState(1007);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Extract_exprContext extends ParserRuleContext {
		public TerminalNode EXTRACT() { return getToken(KVQLParser.EXTRACT, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public TerminalNode FROM() { return getToken(KVQLParser.FROM, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Extract_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_extract_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterExtract_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitExtract_expr(this);
		}
	}

	public final Extract_exprContext extract_expr() throws RecognitionException {
		Extract_exprContext _localctx = new Extract_exprContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_extract_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1009);
			match(EXTRACT);
			setState(1010);
			match(LP);
			setState(1011);
			id();
			setState(1012);
			match(FROM);
			setState(1013);
			expr();
			setState(1014);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Insert_statementContext extends ParserRuleContext {
		public TerminalNode INTO() { return getToken(KVQLParser.INTO, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode VALUES() { return getToken(KVQLParser.VALUES, 0); }
		public List<TerminalNode> LP() { return getTokens(KVQLParser.LP); }
		public TerminalNode LP(int i) {
			return getToken(KVQLParser.LP, i);
		}
		public List<Insert_clauseContext> insert_clause() {
			return getRuleContexts(Insert_clauseContext.class);
		}
		public Insert_clauseContext insert_clause(int i) {
			return getRuleContext(Insert_clauseContext.class,i);
		}
		public List<TerminalNode> RP() { return getTokens(KVQLParser.RP); }
		public TerminalNode RP(int i) {
			return getToken(KVQLParser.RP, i);
		}
		public TerminalNode INSERT() { return getToken(KVQLParser.INSERT, 0); }
		public TerminalNode UPSERT() { return getToken(KVQLParser.UPSERT, 0); }
		public PrologContext prolog() {
			return getRuleContext(PrologContext.class,0);
		}
		public Tab_aliasContext tab_alias() {
			return getRuleContext(Tab_aliasContext.class,0);
		}
		public List<IdContext> id() {
			return getRuleContexts(IdContext.class);
		}
		public IdContext id(int i) {
			return getRuleContext(IdContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public TerminalNode SET() { return getToken(KVQLParser.SET, 0); }
		public TerminalNode TTL() { return getToken(KVQLParser.TTL, 0); }
		public Insert_ttl_clauseContext insert_ttl_clause() {
			return getRuleContext(Insert_ttl_clauseContext.class,0);
		}
		public Insert_returning_clauseContext insert_returning_clause() {
			return getRuleContext(Insert_returning_clauseContext.class,0);
		}
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public Insert_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_insert_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterInsert_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitInsert_statement(this);
		}
	}

	public final Insert_statementContext insert_statement() throws RecognitionException {
		Insert_statementContext _localctx = new Insert_statementContext(_ctx, getState());
		enterRule(_localctx, 136, RULE_insert_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1017);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DECLARE) {
				{
				setState(1016);
				prolog();
				}
			}

			setState(1019);
			_la = _input.LA(1);
			if ( !(_la==INSERT || _la==UPSERT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(1020);
			match(INTO);
			setState(1021);
			table_name();
			setState(1026);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,80,_ctx) ) {
			case 1:
				{
				setState(1023);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,79,_ctx) ) {
				case 1:
					{
					setState(1022);
					match(AS);
					}
					break;
				}
				setState(1025);
				tab_alias();
				}
				break;
			}
			setState(1039);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LP) {
				{
				setState(1028);
				match(LP);
				setState(1029);
				id();
				setState(1034);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(1030);
					match(COMMA);
					setState(1031);
					id();
					}
					}
					setState(1036);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(1037);
				match(RP);
				}
			}

			setState(1041);
			match(VALUES);
			setState(1042);
			match(LP);
			setState(1043);
			insert_clause();
			setState(1048);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1044);
				match(COMMA);
				setState(1045);
				insert_clause();
				}
				}
				setState(1050);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(1051);
			match(RP);
			setState(1055);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SET) {
				{
				setState(1052);
				match(SET);
				setState(1053);
				match(TTL);
				setState(1054);
				insert_ttl_clause();
				}
			}

			setState(1058);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==RETURNING) {
				{
				setState(1057);
				insert_returning_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Insert_returning_clauseContext extends ParserRuleContext {
		public TerminalNode RETURNING() { return getToken(KVQLParser.RETURNING, 0); }
		public Select_listContext select_list() {
			return getRuleContext(Select_listContext.class,0);
		}
		public Insert_returning_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_insert_returning_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterInsert_returning_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitInsert_returning_clause(this);
		}
	}

	public final Insert_returning_clauseContext insert_returning_clause() throws RecognitionException {
		Insert_returning_clauseContext _localctx = new Insert_returning_clauseContext(_ctx, getState());
		enterRule(_localctx, 138, RULE_insert_returning_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1060);
			match(RETURNING);
			setState(1061);
			select_list();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Insert_clauseContext extends ParserRuleContext {
		public TerminalNode DEFAULT() { return getToken(KVQLParser.DEFAULT, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Insert_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_insert_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterInsert_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitInsert_clause(this);
		}
	}

	public final Insert_clauseContext insert_clause() throws RecognitionException {
		Insert_clauseContext _localctx = new Insert_clauseContext(_ctx, getState());
		enterRule(_localctx, 140, RULE_insert_clause);
		try {
			setState(1065);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,86,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1063);
				match(DEFAULT);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1064);
				expr();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Insert_ttl_clauseContext extends ParserRuleContext {
		public Add_exprContext add_expr() {
			return getRuleContext(Add_exprContext.class,0);
		}
		public TerminalNode HOURS() { return getToken(KVQLParser.HOURS, 0); }
		public TerminalNode DAYS() { return getToken(KVQLParser.DAYS, 0); }
		public TerminalNode USING() { return getToken(KVQLParser.USING, 0); }
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public TerminalNode DEFAULT() { return getToken(KVQLParser.DEFAULT, 0); }
		public Insert_ttl_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_insert_ttl_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterInsert_ttl_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitInsert_ttl_clause(this);
		}
	}

	public final Insert_ttl_clauseContext insert_ttl_clause() throws RecognitionException {
		Insert_ttl_clauseContext _localctx = new Insert_ttl_clauseContext(_ctx, getState());
		enterRule(_localctx, 142, RULE_insert_ttl_clause);
		int _la;
		try {
			setState(1073);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,87,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(1067);
				add_expr();
				setState(1068);
				_la = _input.LA(1);
				if ( !(_la==DAYS || _la==HOURS) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(1070);
				match(USING);
				setState(1071);
				match(TABLE);
				setState(1072);
				match(DEFAULT);
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Update_statementContext extends ParserRuleContext {
		public TerminalNode UPDATE() { return getToken(KVQLParser.UPDATE, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public List<Update_clauseContext> update_clause() {
			return getRuleContexts(Update_clauseContext.class);
		}
		public Update_clauseContext update_clause(int i) {
			return getRuleContext(Update_clauseContext.class,i);
		}
		public TerminalNode WHERE() { return getToken(KVQLParser.WHERE, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public PrologContext prolog() {
			return getRuleContext(PrologContext.class,0);
		}
		public Tab_aliasContext tab_alias() {
			return getRuleContext(Tab_aliasContext.class,0);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Update_returning_clauseContext update_returning_clause() {
			return getRuleContext(Update_returning_clauseContext.class,0);
		}
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public Update_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_update_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterUpdate_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitUpdate_statement(this);
		}
	}

	public final Update_statementContext update_statement() throws RecognitionException {
		Update_statementContext _localctx = new Update_statementContext(_ctx, getState());
		enterRule(_localctx, 144, RULE_update_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1076);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DECLARE) {
				{
				setState(1075);
				prolog();
				}
			}

			setState(1078);
			match(UPDATE);
			setState(1079);
			table_name();
			setState(1084);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,90,_ctx) ) {
			case 1:
				{
				setState(1081);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,89,_ctx) ) {
				case 1:
					{
					setState(1080);
					match(AS);
					}
					break;
				}
				setState(1083);
				tab_alias();
				}
				break;
			}
			setState(1086);
			update_clause();
			setState(1091);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1087);
				match(COMMA);
				setState(1088);
				update_clause();
				}
				}
				setState(1093);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(1094);
			match(WHERE);
			setState(1095);
			expr();
			setState(1097);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==RETURNING) {
				{
				setState(1096);
				update_returning_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Update_returning_clauseContext extends ParserRuleContext {
		public TerminalNode RETURNING() { return getToken(KVQLParser.RETURNING, 0); }
		public Select_listContext select_list() {
			return getRuleContext(Select_listContext.class,0);
		}
		public Update_returning_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_update_returning_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterUpdate_returning_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitUpdate_returning_clause(this);
		}
	}

	public final Update_returning_clauseContext update_returning_clause() throws RecognitionException {
		Update_returning_clauseContext _localctx = new Update_returning_clauseContext(_ctx, getState());
		enterRule(_localctx, 146, RULE_update_returning_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1099);
			match(RETURNING);
			setState(1100);
			select_list();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Update_clauseContext extends ParserRuleContext {
		public TerminalNode SET() { return getToken(KVQLParser.SET, 0); }
		public List<Set_clauseContext> set_clause() {
			return getRuleContexts(Set_clauseContext.class);
		}
		public Set_clauseContext set_clause(int i) {
			return getRuleContext(Set_clauseContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public List<Update_clauseContext> update_clause() {
			return getRuleContexts(Update_clauseContext.class);
		}
		public Update_clauseContext update_clause(int i) {
			return getRuleContext(Update_clauseContext.class,i);
		}
		public TerminalNode ADD() { return getToken(KVQLParser.ADD, 0); }
		public List<Add_clauseContext> add_clause() {
			return getRuleContexts(Add_clauseContext.class);
		}
		public Add_clauseContext add_clause(int i) {
			return getRuleContext(Add_clauseContext.class,i);
		}
		public TerminalNode PUT() { return getToken(KVQLParser.PUT, 0); }
		public List<Put_clauseContext> put_clause() {
			return getRuleContexts(Put_clauseContext.class);
		}
		public Put_clauseContext put_clause(int i) {
			return getRuleContext(Put_clauseContext.class,i);
		}
		public TerminalNode REMOVE() { return getToken(KVQLParser.REMOVE, 0); }
		public List<Remove_clauseContext> remove_clause() {
			return getRuleContexts(Remove_clauseContext.class);
		}
		public Remove_clauseContext remove_clause(int i) {
			return getRuleContext(Remove_clauseContext.class,i);
		}
		public TerminalNode TTL() { return getToken(KVQLParser.TTL, 0); }
		public Ttl_clauseContext ttl_clause() {
			return getRuleContext(Ttl_clauseContext.class,0);
		}
		public Update_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_update_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterUpdate_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitUpdate_clause(this);
		}
	}

	public final Update_clauseContext update_clause() throws RecognitionException {
		Update_clauseContext _localctx = new Update_clauseContext(_ctx, getState());
		enterRule(_localctx, 148, RULE_update_clause);
		try {
			int _alt;
			setState(1157);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,101,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(1102);
				match(SET);
				setState(1103);
				set_clause();
				setState(1111);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,94,_ctx);
				while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1104);
						match(COMMA);
						setState(1107);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,93,_ctx) ) {
						case 1:
							{
							setState(1105);
							update_clause();
							}
							break;
						case 2:
							{
							setState(1106);
							set_clause();
							}
							break;
						}
						}
						} 
					}
					setState(1113);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,94,_ctx);
				}
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(1114);
				match(ADD);
				setState(1115);
				add_clause();
				setState(1123);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,96,_ctx);
				while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1116);
						match(COMMA);
						setState(1119);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,95,_ctx) ) {
						case 1:
							{
							setState(1117);
							update_clause();
							}
							break;
						case 2:
							{
							setState(1118);
							add_clause();
							}
							break;
						}
						}
						} 
					}
					setState(1125);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,96,_ctx);
				}
				}
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				{
				setState(1126);
				match(PUT);
				setState(1127);
				put_clause();
				setState(1135);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,98,_ctx);
				while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1128);
						match(COMMA);
						setState(1131);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,97,_ctx) ) {
						case 1:
							{
							setState(1129);
							update_clause();
							}
							break;
						case 2:
							{
							setState(1130);
							put_clause();
							}
							break;
						}
						}
						} 
					}
					setState(1137);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,98,_ctx);
				}
				}
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				{
				setState(1138);
				match(REMOVE);
				setState(1139);
				remove_clause();
				setState(1144);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,99,_ctx);
				while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1140);
						match(COMMA);
						setState(1141);
						remove_clause();
						}
						} 
					}
					setState(1146);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,99,_ctx);
				}
				}
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				{
				setState(1147);
				match(SET);
				setState(1148);
				match(TTL);
				setState(1149);
				ttl_clause();
				setState(1154);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,100,_ctx);
				while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1150);
						match(COMMA);
						setState(1151);
						update_clause();
						}
						} 
					}
					setState(1156);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,100,_ctx);
				}
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Set_clauseContext extends ParserRuleContext {
		public Target_exprContext target_expr() {
			return getRuleContext(Target_exprContext.class,0);
		}
		public TerminalNode EQ() { return getToken(KVQLParser.EQ, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Set_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_set_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSet_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSet_clause(this);
		}
	}

	public final Set_clauseContext set_clause() throws RecognitionException {
		Set_clauseContext _localctx = new Set_clauseContext(_ctx, getState());
		enterRule(_localctx, 150, RULE_set_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1159);
			target_expr();
			setState(1160);
			match(EQ);
			setState(1161);
			expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Add_clauseContext extends ParserRuleContext {
		public Target_exprContext target_expr() {
			return getRuleContext(Target_exprContext.class,0);
		}
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Pos_exprContext pos_expr() {
			return getRuleContext(Pos_exprContext.class,0);
		}
		public Add_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_add_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAdd_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAdd_clause(this);
		}
	}

	public final Add_clauseContext add_clause() throws RecognitionException {
		Add_clauseContext _localctx = new Add_clauseContext(_ctx, getState());
		enterRule(_localctx, 152, RULE_add_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1163);
			target_expr();
			setState(1165);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,102,_ctx) ) {
			case 1:
				{
				setState(1164);
				pos_expr();
				}
				break;
			}
			setState(1167);
			expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Put_clauseContext extends ParserRuleContext {
		public Target_exprContext target_expr() {
			return getRuleContext(Target_exprContext.class,0);
		}
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Put_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_put_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPut_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPut_clause(this);
		}
	}

	public final Put_clauseContext put_clause() throws RecognitionException {
		Put_clauseContext _localctx = new Put_clauseContext(_ctx, getState());
		enterRule(_localctx, 154, RULE_put_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1169);
			target_expr();
			setState(1170);
			expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Remove_clauseContext extends ParserRuleContext {
		public Target_exprContext target_expr() {
			return getRuleContext(Target_exprContext.class,0);
		}
		public Remove_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_remove_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRemove_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRemove_clause(this);
		}
	}

	public final Remove_clauseContext remove_clause() throws RecognitionException {
		Remove_clauseContext _localctx = new Remove_clauseContext(_ctx, getState());
		enterRule(_localctx, 156, RULE_remove_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1172);
			target_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Ttl_clauseContext extends ParserRuleContext {
		public Add_exprContext add_expr() {
			return getRuleContext(Add_exprContext.class,0);
		}
		public TerminalNode HOURS() { return getToken(KVQLParser.HOURS, 0); }
		public TerminalNode DAYS() { return getToken(KVQLParser.DAYS, 0); }
		public TerminalNode USING() { return getToken(KVQLParser.USING, 0); }
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public TerminalNode DEFAULT() { return getToken(KVQLParser.DEFAULT, 0); }
		public Ttl_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ttl_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTtl_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTtl_clause(this);
		}
	}

	public final Ttl_clauseContext ttl_clause() throws RecognitionException {
		Ttl_clauseContext _localctx = new Ttl_clauseContext(_ctx, getState());
		enterRule(_localctx, 158, RULE_ttl_clause);
		int _la;
		try {
			setState(1180);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,103,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(1174);
				add_expr();
				setState(1175);
				_la = _input.LA(1);
				if ( !(_la==DAYS || _la==HOURS) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(1177);
				match(USING);
				setState(1178);
				match(TABLE);
				setState(1179);
				match(DEFAULT);
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Target_exprContext extends ParserRuleContext {
		public Path_exprContext path_expr() {
			return getRuleContext(Path_exprContext.class,0);
		}
		public Target_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_target_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTarget_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTarget_expr(this);
		}
	}

	public final Target_exprContext target_expr() throws RecognitionException {
		Target_exprContext _localctx = new Target_exprContext(_ctx, getState());
		enterRule(_localctx, 160, RULE_target_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1182);
			path_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Pos_exprContext extends ParserRuleContext {
		public Add_exprContext add_expr() {
			return getRuleContext(Add_exprContext.class,0);
		}
		public Pos_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pos_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPos_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPos_expr(this);
		}
	}

	public final Pos_exprContext pos_expr() throws RecognitionException {
		Pos_exprContext _localctx = new Pos_exprContext(_ctx, getState());
		enterRule(_localctx, 162, RULE_pos_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1184);
			add_expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Delete_statementContext extends ParserRuleContext {
		public TerminalNode DELETE() { return getToken(KVQLParser.DELETE, 0); }
		public TerminalNode FROM() { return getToken(KVQLParser.FROM, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public PrologContext prolog() {
			return getRuleContext(PrologContext.class,0);
		}
		public Tab_aliasContext tab_alias() {
			return getRuleContext(Tab_aliasContext.class,0);
		}
		public TerminalNode WHERE() { return getToken(KVQLParser.WHERE, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Delete_returning_clauseContext delete_returning_clause() {
			return getRuleContext(Delete_returning_clauseContext.class,0);
		}
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public Delete_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_delete_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDelete_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDelete_statement(this);
		}
	}

	public final Delete_statementContext delete_statement() throws RecognitionException {
		Delete_statementContext _localctx = new Delete_statementContext(_ctx, getState());
		enterRule(_localctx, 164, RULE_delete_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1187);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DECLARE) {
				{
				setState(1186);
				prolog();
				}
			}

			setState(1189);
			match(DELETE);
			setState(1190);
			match(FROM);
			setState(1191);
			table_name();
			setState(1196);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,106,_ctx) ) {
			case 1:
				{
				setState(1193);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,105,_ctx) ) {
				case 1:
					{
					setState(1192);
					match(AS);
					}
					break;
				}
				setState(1195);
				tab_alias();
				}
				break;
			}
			setState(1200);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(1198);
				match(WHERE);
				setState(1199);
				expr();
				}
			}

			setState(1203);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==RETURNING) {
				{
				setState(1202);
				delete_returning_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Delete_returning_clauseContext extends ParserRuleContext {
		public TerminalNode RETURNING() { return getToken(KVQLParser.RETURNING, 0); }
		public Select_listContext select_list() {
			return getRuleContext(Select_listContext.class,0);
		}
		public Delete_returning_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_delete_returning_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDelete_returning_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDelete_returning_clause(this);
		}
	}

	public final Delete_returning_clauseContext delete_returning_clause() throws RecognitionException {
		Delete_returning_clauseContext _localctx = new Delete_returning_clauseContext(_ctx, getState());
		enterRule(_localctx, 166, RULE_delete_returning_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1205);
			match(RETURNING);
			setState(1206);
			select_list();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Quantified_type_defContext extends ParserRuleContext {
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public TerminalNode STAR() { return getToken(KVQLParser.STAR, 0); }
		public TerminalNode PLUS() { return getToken(KVQLParser.PLUS, 0); }
		public TerminalNode QUESTION_MARK() { return getToken(KVQLParser.QUESTION_MARK, 0); }
		public Quantified_type_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_quantified_type_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterQuantified_type_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitQuantified_type_def(this);
		}
	}

	public final Quantified_type_defContext quantified_type_def() throws RecognitionException {
		Quantified_type_defContext _localctx = new Quantified_type_defContext(_ctx, getState());
		enterRule(_localctx, 168, RULE_quantified_type_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1208);
			type_def();
			setState(1210);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 158)) & ~0x3f) == 0 && ((1L << (_la - 158)) & ((1L << (STAR - 158)) | (1L << (QUESTION_MARK - 158)) | (1L << (PLUS - 158)))) != 0)) {
				{
				setState(1209);
				_la = _input.LA(1);
				if ( !(((((_la - 158)) & ~0x3f) == 0 && ((1L << (_la - 158)) & ((1L << (STAR - 158)) | (1L << (QUESTION_MARK - 158)) | (1L << (PLUS - 158)))) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Type_defContext extends ParserRuleContext {
		public Type_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_type_def; }
	 
		public Type_defContext() { }
		public void copyFrom(Type_defContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class EnumContext extends Type_defContext {
		public Enum_defContext enum_def() {
			return getRuleContext(Enum_defContext.class,0);
		}
		public EnumContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterEnum(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitEnum(this);
		}
	}
	public static class AnyAtomicContext extends Type_defContext {
		public AnyAtomic_defContext anyAtomic_def() {
			return getRuleContext(AnyAtomic_defContext.class,0);
		}
		public AnyAtomicContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAnyAtomic(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAnyAtomic(this);
		}
	}
	public static class AnyJsonAtomicContext extends Type_defContext {
		public AnyJsonAtomic_defContext anyJsonAtomic_def() {
			return getRuleContext(AnyJsonAtomic_defContext.class,0);
		}
		public AnyJsonAtomicContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAnyJsonAtomic(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAnyJsonAtomic(this);
		}
	}
	public static class AnyRecordContext extends Type_defContext {
		public AnyRecord_defContext anyRecord_def() {
			return getRuleContext(AnyRecord_defContext.class,0);
		}
		public AnyRecordContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAnyRecord(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAnyRecord(this);
		}
	}
	public static class JSONContext extends Type_defContext {
		public Json_defContext json_def() {
			return getRuleContext(Json_defContext.class,0);
		}
		public JSONContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJSON(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJSON(this);
		}
	}
	public static class StringTContext extends Type_defContext {
		public String_defContext string_def() {
			return getRuleContext(String_defContext.class,0);
		}
		public StringTContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterStringT(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitStringT(this);
		}
	}
	public static class TimestampContext extends Type_defContext {
		public Timestamp_defContext timestamp_def() {
			return getRuleContext(Timestamp_defContext.class,0);
		}
		public TimestampContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTimestamp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTimestamp(this);
		}
	}
	public static class AnyContext extends Type_defContext {
		public Any_defContext any_def() {
			return getRuleContext(Any_defContext.class,0);
		}
		public AnyContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAny(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAny(this);
		}
	}
	public static class IntContext extends Type_defContext {
		public Integer_defContext integer_def() {
			return getRuleContext(Integer_defContext.class,0);
		}
		public IntContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterInt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitInt(this);
		}
	}
	public static class ArrayContext extends Type_defContext {
		public Array_defContext array_def() {
			return getRuleContext(Array_defContext.class,0);
		}
		public ArrayContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterArray(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitArray(this);
		}
	}
	public static class FloatContext extends Type_defContext {
		public Float_defContext float_def() {
			return getRuleContext(Float_defContext.class,0);
		}
		public FloatContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFloat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFloat(this);
		}
	}
	public static class RecordContext extends Type_defContext {
		public Record_defContext record_def() {
			return getRuleContext(Record_defContext.class,0);
		}
		public RecordContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRecord(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRecord(this);
		}
	}
	public static class BinaryContext extends Type_defContext {
		public Binary_defContext binary_def() {
			return getRuleContext(Binary_defContext.class,0);
		}
		public BinaryContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterBinary(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitBinary(this);
		}
	}
	public static class BooleanContext extends Type_defContext {
		public Boolean_defContext boolean_def() {
			return getRuleContext(Boolean_defContext.class,0);
		}
		public BooleanContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterBoolean(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitBoolean(this);
		}
	}
	public static class MapContext extends Type_defContext {
		public Map_defContext map_def() {
			return getRuleContext(Map_defContext.class,0);
		}
		public MapContext(Type_defContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterMap(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitMap(this);
		}
	}

	public final Type_defContext type_def() throws RecognitionException {
		Type_defContext _localctx = new Type_defContext(_ctx, getState());
		enterRule(_localctx, 170, RULE_type_def);
		try {
			setState(1227);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BINARY_T:
				_localctx = new BinaryContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(1212);
				binary_def();
				}
				break;
			case ARRAY_T:
				_localctx = new ArrayContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(1213);
				array_def();
				}
				break;
			case BOOLEAN_T:
				_localctx = new BooleanContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(1214);
				boolean_def();
				}
				break;
			case ENUM_T:
				_localctx = new EnumContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(1215);
				enum_def();
				}
				break;
			case DOUBLE_T:
			case FLOAT_T:
			case NUMBER_T:
				_localctx = new FloatContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(1216);
				float_def();
				}
				break;
			case INTEGER_T:
			case LONG_T:
				_localctx = new IntContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(1217);
				integer_def();
				}
				break;
			case JSON:
				_localctx = new JSONContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(1218);
				json_def();
				}
				break;
			case MAP_T:
				_localctx = new MapContext(_localctx);
				enterOuterAlt(_localctx, 8);
				{
				setState(1219);
				map_def();
				}
				break;
			case RECORD_T:
				_localctx = new RecordContext(_localctx);
				enterOuterAlt(_localctx, 9);
				{
				setState(1220);
				record_def();
				}
				break;
			case STRING_T:
				_localctx = new StringTContext(_localctx);
				enterOuterAlt(_localctx, 10);
				{
				setState(1221);
				string_def();
				}
				break;
			case TIMESTAMP_T:
				_localctx = new TimestampContext(_localctx);
				enterOuterAlt(_localctx, 11);
				{
				setState(1222);
				timestamp_def();
				}
				break;
			case ANY_T:
				_localctx = new AnyContext(_localctx);
				enterOuterAlt(_localctx, 12);
				{
				setState(1223);
				any_def();
				}
				break;
			case ANYATOMIC_T:
				_localctx = new AnyAtomicContext(_localctx);
				enterOuterAlt(_localctx, 13);
				{
				setState(1224);
				anyAtomic_def();
				}
				break;
			case ANYJSONATOMIC_T:
				_localctx = new AnyJsonAtomicContext(_localctx);
				enterOuterAlt(_localctx, 14);
				{
				setState(1225);
				anyJsonAtomic_def();
				}
				break;
			case ANYRECORD_T:
				_localctx = new AnyRecordContext(_localctx);
				enterOuterAlt(_localctx, 15);
				{
				setState(1226);
				anyRecord_def();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Record_defContext extends ParserRuleContext {
		public TerminalNode RECORD_T() { return getToken(KVQLParser.RECORD_T, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public List<Field_defContext> field_def() {
			return getRuleContexts(Field_defContext.class);
		}
		public Field_defContext field_def(int i) {
			return getRuleContext(Field_defContext.class,i);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Record_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_record_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRecord_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRecord_def(this);
		}
	}

	public final Record_defContext record_def() throws RecognitionException {
		Record_defContext _localctx = new Record_defContext(_ctx, getState());
		enterRule(_localctx, 172, RULE_record_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1229);
			match(RECORD_T);
			setState(1230);
			match(LP);
			setState(1231);
			field_def();
			setState(1236);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1232);
				match(COMMA);
				setState(1233);
				field_def();
				}
				}
				setState(1238);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(1239);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Field_defContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public Default_defContext default_def() {
			return getRuleContext(Default_defContext.class,0);
		}
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public Field_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_field_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterField_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitField_def(this);
		}
	}

	public final Field_defContext field_def() throws RecognitionException {
		Field_defContext _localctx = new Field_defContext(_ctx, getState());
		enterRule(_localctx, 174, RULE_field_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1241);
			id();
			setState(1242);
			type_def();
			setState(1244);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DEFAULT || _la==NOT) {
				{
				setState(1243);
				default_def();
				}
			}

			setState(1247);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMENT) {
				{
				setState(1246);
				comment();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Default_defContext extends ParserRuleContext {
		public Default_valueContext default_value() {
			return getRuleContext(Default_valueContext.class,0);
		}
		public Not_nullContext not_null() {
			return getRuleContext(Not_nullContext.class,0);
		}
		public Default_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_default_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDefault_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDefault_def(this);
		}
	}

	public final Default_defContext default_def() throws RecognitionException {
		Default_defContext _localctx = new Default_defContext(_ctx, getState());
		enterRule(_localctx, 176, RULE_default_def);
		int _la;
		try {
			setState(1257);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEFAULT:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(1249);
				default_value();
				setState(1251);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(1250);
					not_null();
					}
				}

				}
				}
				break;
			case NOT:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(1253);
				not_null();
				setState(1255);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==DEFAULT) {
					{
					setState(1254);
					default_value();
					}
				}

				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Default_valueContext extends ParserRuleContext {
		public TerminalNode DEFAULT() { return getToken(KVQLParser.DEFAULT, 0); }
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public TerminalNode TRUE() { return getToken(KVQLParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(KVQLParser.FALSE, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Default_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_default_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDefault_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDefault_value(this);
		}
	}

	public final Default_valueContext default_value() throws RecognitionException {
		Default_valueContext _localctx = new Default_valueContext(_ctx, getState());
		enterRule(_localctx, 178, RULE_default_value);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1259);
			match(DEFAULT);
			setState(1265);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MINUS:
			case INT:
			case FLOAT:
			case NUMBER:
				{
				setState(1260);
				number();
				}
				break;
			case DSTRING:
			case STRING:
				{
				setState(1261);
				string();
				}
				break;
			case TRUE:
				{
				setState(1262);
				match(TRUE);
				}
				break;
			case FALSE:
				{
				setState(1263);
				match(FALSE);
				}
				break;
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case RDIV:
			case ID:
			case BAD_ID:
				{
				setState(1264);
				id();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Not_nullContext extends ParserRuleContext {
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public TerminalNode NULL() { return getToken(KVQLParser.NULL, 0); }
		public Not_nullContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_not_null; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterNot_null(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitNot_null(this);
		}
	}

	public final Not_nullContext not_null() throws RecognitionException {
		Not_nullContext _localctx = new Not_nullContext(_ctx, getState());
		enterRule(_localctx, 180, RULE_not_null);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1267);
			match(NOT);
			setState(1268);
			match(NULL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Map_defContext extends ParserRuleContext {
		public TerminalNode MAP_T() { return getToken(KVQLParser.MAP_T, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Map_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_map_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterMap_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitMap_def(this);
		}
	}

	public final Map_defContext map_def() throws RecognitionException {
		Map_defContext _localctx = new Map_defContext(_ctx, getState());
		enterRule(_localctx, 182, RULE_map_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1270);
			match(MAP_T);
			setState(1271);
			match(LP);
			setState(1272);
			type_def();
			setState(1273);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Array_defContext extends ParserRuleContext {
		public TerminalNode ARRAY_T() { return getToken(KVQLParser.ARRAY_T, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Array_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterArray_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitArray_def(this);
		}
	}

	public final Array_defContext array_def() throws RecognitionException {
		Array_defContext _localctx = new Array_defContext(_ctx, getState());
		enterRule(_localctx, 184, RULE_array_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1275);
			match(ARRAY_T);
			setState(1276);
			match(LP);
			setState(1277);
			type_def();
			setState(1278);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Integer_defContext extends ParserRuleContext {
		public TerminalNode INTEGER_T() { return getToken(KVQLParser.INTEGER_T, 0); }
		public TerminalNode LONG_T() { return getToken(KVQLParser.LONG_T, 0); }
		public Integer_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_integer_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterInteger_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitInteger_def(this);
		}
	}

	public final Integer_defContext integer_def() throws RecognitionException {
		Integer_defContext _localctx = new Integer_defContext(_ctx, getState());
		enterRule(_localctx, 186, RULE_integer_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1280);
			_la = _input.LA(1);
			if ( !(_la==INTEGER_T || _la==LONG_T) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Json_defContext extends ParserRuleContext {
		public TerminalNode JSON() { return getToken(KVQLParser.JSON, 0); }
		public Json_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_json_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJson_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJson_def(this);
		}
	}

	public final Json_defContext json_def() throws RecognitionException {
		Json_defContext _localctx = new Json_defContext(_ctx, getState());
		enterRule(_localctx, 188, RULE_json_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1282);
			match(JSON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Float_defContext extends ParserRuleContext {
		public TerminalNode FLOAT_T() { return getToken(KVQLParser.FLOAT_T, 0); }
		public TerminalNode DOUBLE_T() { return getToken(KVQLParser.DOUBLE_T, 0); }
		public TerminalNode NUMBER_T() { return getToken(KVQLParser.NUMBER_T, 0); }
		public Float_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_float_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFloat_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFloat_def(this);
		}
	}

	public final Float_defContext float_def() throws RecognitionException {
		Float_defContext _localctx = new Float_defContext(_ctx, getState());
		enterRule(_localctx, 190, RULE_float_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1284);
			_la = _input.LA(1);
			if ( !(((((_la - 132)) & ~0x3f) == 0 && ((1L << (_la - 132)) & ((1L << (DOUBLE_T - 132)) | (1L << (FLOAT_T - 132)) | (1L << (NUMBER_T - 132)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class String_defContext extends ParserRuleContext {
		public TerminalNode STRING_T() { return getToken(KVQLParser.STRING_T, 0); }
		public String_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_string_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterString_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitString_def(this);
		}
	}

	public final String_defContext string_def() throws RecognitionException {
		String_defContext _localctx = new String_defContext(_ctx, getState());
		enterRule(_localctx, 192, RULE_string_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1286);
			match(STRING_T);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Enum_defContext extends ParserRuleContext {
		public TerminalNode ENUM_T() { return getToken(KVQLParser.ENUM_T, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Id_listContext id_list() {
			return getRuleContext(Id_listContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Enum_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enum_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterEnum_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitEnum_def(this);
		}
	}

	public final Enum_defContext enum_def() throws RecognitionException {
		Enum_defContext _localctx = new Enum_defContext(_ctx, getState());
		enterRule(_localctx, 194, RULE_enum_def);
		try {
			setState(1298);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,118,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(1288);
				match(ENUM_T);
				setState(1289);
				match(LP);
				setState(1290);
				id_list();
				setState(1291);
				match(RP);
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(1293);
				match(ENUM_T);
				setState(1294);
				match(LP);
				setState(1295);
				id_list();
				 notifyErrorListeners("Missing closing ')'"); 
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Boolean_defContext extends ParserRuleContext {
		public TerminalNode BOOLEAN_T() { return getToken(KVQLParser.BOOLEAN_T, 0); }
		public Boolean_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_boolean_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterBoolean_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitBoolean_def(this);
		}
	}

	public final Boolean_defContext boolean_def() throws RecognitionException {
		Boolean_defContext _localctx = new Boolean_defContext(_ctx, getState());
		enterRule(_localctx, 196, RULE_boolean_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1300);
			match(BOOLEAN_T);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Binary_defContext extends ParserRuleContext {
		public TerminalNode BINARY_T() { return getToken(KVQLParser.BINARY_T, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Binary_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binary_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterBinary_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitBinary_def(this);
		}
	}

	public final Binary_defContext binary_def() throws RecognitionException {
		Binary_defContext _localctx = new Binary_defContext(_ctx, getState());
		enterRule(_localctx, 198, RULE_binary_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1302);
			match(BINARY_T);
			setState(1306);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LP) {
				{
				setState(1303);
				match(LP);
				setState(1304);
				match(INT);
				setState(1305);
				match(RP);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Timestamp_defContext extends ParserRuleContext {
		public TerminalNode TIMESTAMP_T() { return getToken(KVQLParser.TIMESTAMP_T, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Timestamp_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_timestamp_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTimestamp_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTimestamp_def(this);
		}
	}

	public final Timestamp_defContext timestamp_def() throws RecognitionException {
		Timestamp_defContext _localctx = new Timestamp_defContext(_ctx, getState());
		enterRule(_localctx, 200, RULE_timestamp_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1308);
			match(TIMESTAMP_T);
			setState(1312);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LP) {
				{
				setState(1309);
				match(LP);
				setState(1310);
				match(INT);
				setState(1311);
				match(RP);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Any_defContext extends ParserRuleContext {
		public TerminalNode ANY_T() { return getToken(KVQLParser.ANY_T, 0); }
		public Any_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_any_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAny_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAny_def(this);
		}
	}

	public final Any_defContext any_def() throws RecognitionException {
		Any_defContext _localctx = new Any_defContext(_ctx, getState());
		enterRule(_localctx, 202, RULE_any_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1314);
			match(ANY_T);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnyAtomic_defContext extends ParserRuleContext {
		public TerminalNode ANYATOMIC_T() { return getToken(KVQLParser.ANYATOMIC_T, 0); }
		public AnyAtomic_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_anyAtomic_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAnyAtomic_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAnyAtomic_def(this);
		}
	}

	public final AnyAtomic_defContext anyAtomic_def() throws RecognitionException {
		AnyAtomic_defContext _localctx = new AnyAtomic_defContext(_ctx, getState());
		enterRule(_localctx, 204, RULE_anyAtomic_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1316);
			match(ANYATOMIC_T);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnyJsonAtomic_defContext extends ParserRuleContext {
		public TerminalNode ANYJSONATOMIC_T() { return getToken(KVQLParser.ANYJSONATOMIC_T, 0); }
		public AnyJsonAtomic_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_anyJsonAtomic_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAnyJsonAtomic_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAnyJsonAtomic_def(this);
		}
	}

	public final AnyJsonAtomic_defContext anyJsonAtomic_def() throws RecognitionException {
		AnyJsonAtomic_defContext _localctx = new AnyJsonAtomic_defContext(_ctx, getState());
		enterRule(_localctx, 206, RULE_anyJsonAtomic_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1318);
			match(ANYJSONATOMIC_T);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnyRecord_defContext extends ParserRuleContext {
		public TerminalNode ANYRECORD_T() { return getToken(KVQLParser.ANYRECORD_T, 0); }
		public AnyRecord_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_anyRecord_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAnyRecord_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAnyRecord_def(this);
		}
	}

	public final AnyRecord_defContext anyRecord_def() throws RecognitionException {
		AnyRecord_defContext _localctx = new AnyRecord_defContext(_ctx, getState());
		enterRule(_localctx, 208, RULE_anyRecord_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1320);
			match(ANYRECORD_T);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Id_pathContext extends ParserRuleContext {
		public List<IdContext> id() {
			return getRuleContexts(IdContext.class);
		}
		public IdContext id(int i) {
			return getRuleContext(IdContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(KVQLParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(KVQLParser.DOT, i);
		}
		public Id_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_id_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterId_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitId_path(this);
		}
	}

	public final Id_pathContext id_path() throws RecognitionException {
		Id_pathContext _localctx = new Id_pathContext(_ctx, getState());
		enterRule(_localctx, 210, RULE_id_path);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1322);
			id();
			setState(1327);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(1323);
				match(DOT);
				setState(1324);
				id();
				}
				}
				setState(1329);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Table_id_pathContext extends ParserRuleContext {
		public List<Table_idContext> table_id() {
			return getRuleContexts(Table_idContext.class);
		}
		public Table_idContext table_id(int i) {
			return getRuleContext(Table_idContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(KVQLParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(KVQLParser.DOT, i);
		}
		public Table_id_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_table_id_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTable_id_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTable_id_path(this);
		}
	}

	public final Table_id_pathContext table_id_path() throws RecognitionException {
		Table_id_pathContext _localctx = new Table_id_pathContext(_ctx, getState());
		enterRule(_localctx, 212, RULE_table_id_path);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1330);
			table_id();
			setState(1335);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(1331);
				match(DOT);
				setState(1332);
				table_id();
				}
				}
				setState(1337);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Table_idContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public TerminalNode SYSDOLAR() { return getToken(KVQLParser.SYSDOLAR, 0); }
		public Table_idContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_table_id; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTable_id(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTable_id(this);
		}
	}

	public final Table_idContext table_id() throws RecognitionException {
		Table_idContext _localctx = new Table_idContext(_ctx, getState());
		enterRule(_localctx, 214, RULE_table_id);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1339);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SYSDOLAR) {
				{
				setState(1338);
				match(SYSDOLAR);
				}
			}

			setState(1341);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Name_pathContext extends ParserRuleContext {
		public List<Field_nameContext> field_name() {
			return getRuleContexts(Field_nameContext.class);
		}
		public Field_nameContext field_name(int i) {
			return getRuleContext(Field_nameContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(KVQLParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(KVQLParser.DOT, i);
		}
		public Name_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterName_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitName_path(this);
		}
	}

	public final Name_pathContext name_path() throws RecognitionException {
		Name_pathContext _localctx = new Name_pathContext(_ctx, getState());
		enterRule(_localctx, 216, RULE_name_path);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1343);
			field_name();
			setState(1348);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,124,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1344);
					match(DOT);
					setState(1345);
					field_name();
					}
					} 
				}
				setState(1350);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,124,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Field_nameContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public TerminalNode DSTRING() { return getToken(KVQLParser.DSTRING, 0); }
		public Field_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_field_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterField_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitField_name(this);
		}
	}

	public final Field_nameContext field_name() throws RecognitionException {
		Field_nameContext _localctx = new Field_nameContext(_ctx, getState());
		enterRule(_localctx, 218, RULE_field_name);
		try {
			setState(1353);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case RDIV:
			case ID:
			case BAD_ID:
				enterOuterAlt(_localctx, 1);
				{
				setState(1351);
				id();
				}
				break;
			case DSTRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(1352);
				match(DSTRING);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_namespace_statementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode NAMESPACE() { return getToken(KVQLParser.NAMESPACE, 0); }
		public NamespaceContext namespace() {
			return getRuleContext(NamespaceContext.class,0);
		}
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public Create_namespace_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_namespace_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_namespace_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_namespace_statement(this);
		}
	}

	public final Create_namespace_statementContext create_namespace_statement() throws RecognitionException {
		Create_namespace_statementContext _localctx = new Create_namespace_statementContext(_ctx, getState());
		enterRule(_localctx, 220, RULE_create_namespace_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1355);
			match(CREATE);
			setState(1356);
			match(NAMESPACE);
			setState(1360);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,126,_ctx) ) {
			case 1:
				{
				setState(1357);
				match(IF);
				setState(1358);
				match(NOT);
				setState(1359);
				match(EXISTS);
				}
				break;
			}
			setState(1362);
			namespace();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_namespace_statementContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode NAMESPACE() { return getToken(KVQLParser.NAMESPACE, 0); }
		public NamespaceContext namespace() {
			return getRuleContext(NamespaceContext.class,0);
		}
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public TerminalNode CASCADE() { return getToken(KVQLParser.CASCADE, 0); }
		public Drop_namespace_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_namespace_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_namespace_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_namespace_statement(this);
		}
	}

	public final Drop_namespace_statementContext drop_namespace_statement() throws RecognitionException {
		Drop_namespace_statementContext _localctx = new Drop_namespace_statementContext(_ctx, getState());
		enterRule(_localctx, 222, RULE_drop_namespace_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1364);
			match(DROP);
			setState(1365);
			match(NAMESPACE);
			setState(1368);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,127,_ctx) ) {
			case 1:
				{
				setState(1366);
				match(IF);
				setState(1367);
				match(EXISTS);
				}
				break;
			}
			setState(1370);
			namespace();
			setState(1372);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==CASCADE) {
				{
				setState(1371);
				match(CASCADE);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Region_nameContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Region_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_region_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRegion_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRegion_name(this);
		}
	}

	public final Region_nameContext region_name() throws RecognitionException {
		Region_nameContext _localctx = new Region_nameContext(_ctx, getState());
		enterRule(_localctx, 224, RULE_region_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1374);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_region_statementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode REGION() { return getToken(KVQLParser.REGION, 0); }
		public Region_nameContext region_name() {
			return getRuleContext(Region_nameContext.class,0);
		}
		public Create_region_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_region_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_region_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_region_statement(this);
		}
	}

	public final Create_region_statementContext create_region_statement() throws RecognitionException {
		Create_region_statementContext _localctx = new Create_region_statementContext(_ctx, getState());
		enterRule(_localctx, 226, RULE_create_region_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1376);
			match(CREATE);
			setState(1377);
			match(REGION);
			setState(1378);
			region_name();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_region_statementContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode REGION() { return getToken(KVQLParser.REGION, 0); }
		public Region_nameContext region_name() {
			return getRuleContext(Region_nameContext.class,0);
		}
		public Drop_region_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_region_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_region_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_region_statement(this);
		}
	}

	public final Drop_region_statementContext drop_region_statement() throws RecognitionException {
		Drop_region_statementContext _localctx = new Drop_region_statementContext(_ctx, getState());
		enterRule(_localctx, 228, RULE_drop_region_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1380);
			match(DROP);
			setState(1381);
			match(REGION);
			setState(1382);
			region_name();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Set_local_region_statementContext extends ParserRuleContext {
		public TerminalNode SET() { return getToken(KVQLParser.SET, 0); }
		public TerminalNode LOCAL() { return getToken(KVQLParser.LOCAL, 0); }
		public TerminalNode REGION() { return getToken(KVQLParser.REGION, 0); }
		public Region_nameContext region_name() {
			return getRuleContext(Region_nameContext.class,0);
		}
		public Set_local_region_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_set_local_region_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSet_local_region_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSet_local_region_statement(this);
		}
	}

	public final Set_local_region_statementContext set_local_region_statement() throws RecognitionException {
		Set_local_region_statementContext _localctx = new Set_local_region_statementContext(_ctx, getState());
		enterRule(_localctx, 230, RULE_set_local_region_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1384);
			match(SET);
			setState(1385);
			match(LOCAL);
			setState(1386);
			match(REGION);
			setState(1387);
			region_name();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_table_statementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Table_defContext table_def() {
			return getRuleContext(Table_defContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Table_optionsContext table_options() {
			return getRuleContext(Table_optionsContext.class,0);
		}
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public Create_table_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_table_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_table_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_table_statement(this);
		}
	}

	public final Create_table_statementContext create_table_statement() throws RecognitionException {
		Create_table_statementContext _localctx = new Create_table_statementContext(_ctx, getState());
		enterRule(_localctx, 232, RULE_create_table_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1389);
			match(CREATE);
			setState(1390);
			match(TABLE);
			setState(1394);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,129,_ctx) ) {
			case 1:
				{
				setState(1391);
				match(IF);
				setState(1392);
				match(NOT);
				setState(1393);
				match(EXISTS);
				}
				break;
			}
			setState(1396);
			table_name();
			setState(1398);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMENT) {
				{
				setState(1397);
				comment();
				}
			}

			setState(1400);
			match(LP);
			setState(1401);
			table_def();
			setState(1402);
			match(RP);
			setState(1403);
			table_options();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Table_nameContext extends ParserRuleContext {
		public Table_id_pathContext table_id_path() {
			return getRuleContext(Table_id_pathContext.class,0);
		}
		public NamespaceContext namespace() {
			return getRuleContext(NamespaceContext.class,0);
		}
		public TerminalNode COLON() { return getToken(KVQLParser.COLON, 0); }
		public Table_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_table_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTable_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTable_name(this);
		}
	}

	public final Table_nameContext table_name() throws RecognitionException {
		Table_nameContext _localctx = new Table_nameContext(_ctx, getState());
		enterRule(_localctx, 234, RULE_table_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1408);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,131,_ctx) ) {
			case 1:
				{
				setState(1405);
				namespace();
				setState(1406);
				match(COLON);
				}
				break;
			}
			setState(1410);
			table_id_path();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NamespaceContext extends ParserRuleContext {
		public Id_pathContext id_path() {
			return getRuleContext(Id_pathContext.class,0);
		}
		public NamespaceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namespace; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterNamespace(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitNamespace(this);
		}
	}

	public final NamespaceContext namespace() throws RecognitionException {
		NamespaceContext _localctx = new NamespaceContext(_ctx, getState());
		enterRule(_localctx, 236, RULE_namespace);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1412);
			id_path();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Table_defContext extends ParserRuleContext {
		public List<Column_defContext> column_def() {
			return getRuleContexts(Column_defContext.class);
		}
		public Column_defContext column_def(int i) {
			return getRuleContext(Column_defContext.class,i);
		}
		public List<Key_defContext> key_def() {
			return getRuleContexts(Key_defContext.class);
		}
		public Key_defContext key_def(int i) {
			return getRuleContext(Key_defContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Table_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_table_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTable_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTable_def(this);
		}
	}

	public final Table_defContext table_def() throws RecognitionException {
		Table_defContext _localctx = new Table_defContext(_ctx, getState());
		enterRule(_localctx, 238, RULE_table_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1416);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,132,_ctx) ) {
			case 1:
				{
				setState(1414);
				column_def();
				}
				break;
			case 2:
				{
				setState(1415);
				key_def();
				}
				break;
			}
			setState(1425);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1418);
				match(COMMA);
				setState(1421);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,133,_ctx) ) {
				case 1:
					{
					setState(1419);
					column_def();
					}
					break;
				case 2:
					{
					setState(1420);
					key_def();
					}
					break;
				}
				}
				}
				setState(1427);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Column_defContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public Default_defContext default_def() {
			return getRuleContext(Default_defContext.class,0);
		}
		public Identity_defContext identity_def() {
			return getRuleContext(Identity_defContext.class,0);
		}
		public Uuid_defContext uuid_def() {
			return getRuleContext(Uuid_defContext.class,0);
		}
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public Column_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_column_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterColumn_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitColumn_def(this);
		}
	}

	public final Column_defContext column_def() throws RecognitionException {
		Column_defContext _localctx = new Column_defContext(_ctx, getState());
		enterRule(_localctx, 240, RULE_column_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1428);
			id();
			setState(1429);
			type_def();
			setState(1433);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEFAULT:
			case NOT:
				{
				setState(1430);
				default_def();
				}
				break;
			case GENERATED:
				{
				setState(1431);
				identity_def();
				}
				break;
			case AS:
				{
				setState(1432);
				uuid_def();
				}
				break;
			case COMMENT:
			case COMMA:
			case RP:
				break;
			default:
				break;
			}
			setState(1436);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMENT) {
				{
				setState(1435);
				comment();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Key_defContext extends ParserRuleContext {
		public TerminalNode PRIMARY() { return getToken(KVQLParser.PRIMARY, 0); }
		public TerminalNode KEY() { return getToken(KVQLParser.KEY, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Shard_key_defContext shard_key_def() {
			return getRuleContext(Shard_key_defContext.class,0);
		}
		public Id_list_with_sizeContext id_list_with_size() {
			return getRuleContext(Id_list_with_sizeContext.class,0);
		}
		public TerminalNode COMMA() { return getToken(KVQLParser.COMMA, 0); }
		public Key_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_key_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterKey_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitKey_def(this);
		}
	}

	public final Key_defContext key_def() throws RecognitionException {
		Key_defContext _localctx = new Key_defContext(_ctx, getState());
		enterRule(_localctx, 242, RULE_key_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1438);
			match(PRIMARY);
			setState(1439);
			match(KEY);
			setState(1440);
			match(LP);
			setState(1445);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,138,_ctx) ) {
			case 1:
				{
				setState(1441);
				shard_key_def();
				setState(1443);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1442);
					match(COMMA);
					}
				}

				}
				break;
			}
			setState(1448);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (RDIV - 129)) | (1L << (ID - 129)) | (1L << (BAD_ID - 129)))) != 0)) {
				{
				setState(1447);
				id_list_with_size();
				}
			}

			setState(1450);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Shard_key_defContext extends ParserRuleContext {
		public TerminalNode SHARD() { return getToken(KVQLParser.SHARD, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Id_list_with_sizeContext id_list_with_size() {
			return getRuleContext(Id_list_with_sizeContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Shard_key_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_shard_key_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterShard_key_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitShard_key_def(this);
		}
	}

	public final Shard_key_defContext shard_key_def() throws RecognitionException {
		Shard_key_defContext _localctx = new Shard_key_defContext(_ctx, getState());
		enterRule(_localctx, 244, RULE_shard_key_def);
		try {
			setState(1461);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SHARD:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(1452);
				match(SHARD);
				setState(1453);
				match(LP);
				setState(1454);
				id_list_with_size();
				setState(1455);
				match(RP);
				}
				}
				break;
			case LP:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(1457);
				match(LP);
				setState(1458);
				id_list_with_size();
				 notifyErrorListeners("Missing closing ')'"); 
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Id_list_with_sizeContext extends ParserRuleContext {
		public List<Id_with_sizeContext> id_with_size() {
			return getRuleContexts(Id_with_sizeContext.class);
		}
		public Id_with_sizeContext id_with_size(int i) {
			return getRuleContext(Id_with_sizeContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Id_list_with_sizeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_id_list_with_size; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterId_list_with_size(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitId_list_with_size(this);
		}
	}

	public final Id_list_with_sizeContext id_list_with_size() throws RecognitionException {
		Id_list_with_sizeContext _localctx = new Id_list_with_sizeContext(_ctx, getState());
		enterRule(_localctx, 246, RULE_id_list_with_size);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1463);
			id_with_size();
			setState(1468);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,141,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1464);
					match(COMMA);
					setState(1465);
					id_with_size();
					}
					} 
				}
				setState(1470);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,141,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Id_with_sizeContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Storage_sizeContext storage_size() {
			return getRuleContext(Storage_sizeContext.class,0);
		}
		public Id_with_sizeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_id_with_size; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterId_with_size(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitId_with_size(this);
		}
	}

	public final Id_with_sizeContext id_with_size() throws RecognitionException {
		Id_with_sizeContext _localctx = new Id_with_sizeContext(_ctx, getState());
		enterRule(_localctx, 248, RULE_id_with_size);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1471);
			id();
			setState(1473);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LP) {
				{
				setState(1472);
				storage_size();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Storage_sizeContext extends ParserRuleContext {
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Storage_sizeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_storage_size; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterStorage_size(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitStorage_size(this);
		}
	}

	public final Storage_sizeContext storage_size() throws RecognitionException {
		Storage_sizeContext _localctx = new Storage_sizeContext(_ctx, getState());
		enterRule(_localctx, 250, RULE_storage_size);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1475);
			match(LP);
			setState(1476);
			match(INT);
			setState(1477);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Table_optionsContext extends ParserRuleContext {
		public Ttl_defContext ttl_def() {
			return getRuleContext(Ttl_defContext.class,0);
		}
		public Regions_defContext regions_def() {
			return getRuleContext(Regions_defContext.class,0);
		}
		public Table_optionsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_table_options; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTable_options(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTable_options(this);
		}
	}

	public final Table_optionsContext table_options() throws RecognitionException {
		Table_optionsContext _localctx = new Table_optionsContext(_ctx, getState());
		enterRule(_localctx, 252, RULE_table_options);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1491);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,147,_ctx) ) {
			case 1:
				{
				setState(1480);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==USING) {
					{
					setState(1479);
					ttl_def();
					}
				}

				setState(1483);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==IN) {
					{
					setState(1482);
					regions_def();
					}
				}

				}
				break;
			case 2:
				{
				setState(1486);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==IN) {
					{
					setState(1485);
					regions_def();
					}
				}

				setState(1489);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==USING) {
					{
					setState(1488);
					ttl_def();
					}
				}

				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Ttl_defContext extends ParserRuleContext {
		public TerminalNode USING() { return getToken(KVQLParser.USING, 0); }
		public TerminalNode TTL() { return getToken(KVQLParser.TTL, 0); }
		public DurationContext duration() {
			return getRuleContext(DurationContext.class,0);
		}
		public Ttl_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ttl_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTtl_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTtl_def(this);
		}
	}

	public final Ttl_defContext ttl_def() throws RecognitionException {
		Ttl_defContext _localctx = new Ttl_defContext(_ctx, getState());
		enterRule(_localctx, 254, RULE_ttl_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1493);
			match(USING);
			setState(1494);
			match(TTL);
			setState(1495);
			duration();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Region_namesContext extends ParserRuleContext {
		public Id_listContext id_list() {
			return getRuleContext(Id_listContext.class,0);
		}
		public Region_namesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_region_names; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRegion_names(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRegion_names(this);
		}
	}

	public final Region_namesContext region_names() throws RecognitionException {
		Region_namesContext _localctx = new Region_namesContext(_ctx, getState());
		enterRule(_localctx, 256, RULE_region_names);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1497);
			id_list();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Regions_defContext extends ParserRuleContext {
		public TerminalNode IN() { return getToken(KVQLParser.IN, 0); }
		public TerminalNode REGIONS() { return getToken(KVQLParser.REGIONS, 0); }
		public Region_namesContext region_names() {
			return getRuleContext(Region_namesContext.class,0);
		}
		public Regions_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_regions_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRegions_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRegions_def(this);
		}
	}

	public final Regions_defContext regions_def() throws RecognitionException {
		Regions_defContext _localctx = new Regions_defContext(_ctx, getState());
		enterRule(_localctx, 258, RULE_regions_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1499);
			match(IN);
			setState(1500);
			match(REGIONS);
			setState(1501);
			region_names();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Add_region_defContext extends ParserRuleContext {
		public TerminalNode ADD() { return getToken(KVQLParser.ADD, 0); }
		public TerminalNode REGIONS() { return getToken(KVQLParser.REGIONS, 0); }
		public Region_namesContext region_names() {
			return getRuleContext(Region_namesContext.class,0);
		}
		public Add_region_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_add_region_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAdd_region_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAdd_region_def(this);
		}
	}

	public final Add_region_defContext add_region_def() throws RecognitionException {
		Add_region_defContext _localctx = new Add_region_defContext(_ctx, getState());
		enterRule(_localctx, 260, RULE_add_region_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1503);
			match(ADD);
			setState(1504);
			match(REGIONS);
			setState(1505);
			region_names();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_region_defContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode REGIONS() { return getToken(KVQLParser.REGIONS, 0); }
		public Region_namesContext region_names() {
			return getRuleContext(Region_namesContext.class,0);
		}
		public Drop_region_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_region_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_region_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_region_def(this);
		}
	}

	public final Drop_region_defContext drop_region_def() throws RecognitionException {
		Drop_region_defContext _localctx = new Drop_region_defContext(_ctx, getState());
		enterRule(_localctx, 262, RULE_drop_region_def);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1507);
			match(DROP);
			setState(1508);
			match(REGIONS);
			setState(1509);
			region_names();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Identity_defContext extends ParserRuleContext {
		public TerminalNode GENERATED() { return getToken(KVQLParser.GENERATED, 0); }
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public TerminalNode IDENTITY() { return getToken(KVQLParser.IDENTITY, 0); }
		public TerminalNode ALWAYS() { return getToken(KVQLParser.ALWAYS, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public TerminalNode BY() { return getToken(KVQLParser.BY, 0); }
		public TerminalNode DEFAULT() { return getToken(KVQLParser.DEFAULT, 0); }
		public List<Sequence_optionsContext> sequence_options() {
			return getRuleContexts(Sequence_optionsContext.class);
		}
		public Sequence_optionsContext sequence_options(int i) {
			return getRuleContext(Sequence_optionsContext.class,i);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public TerminalNode NULL() { return getToken(KVQLParser.NULL, 0); }
		public Identity_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identity_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIdentity_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIdentity_def(this);
		}
	}

	public final Identity_defContext identity_def() throws RecognitionException {
		Identity_defContext _localctx = new Identity_defContext(_ctx, getState());
		enterRule(_localctx, 264, RULE_identity_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1511);
			match(GENERATED);
			setState(1519);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ALWAYS:
				{
				setState(1512);
				match(ALWAYS);
				}
				break;
			case BY:
				{
				{
				setState(1513);
				match(BY);
				setState(1514);
				match(DEFAULT);
				setState(1517);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ON) {
					{
					setState(1515);
					match(ON);
					setState(1516);
					match(NULL);
					}
				}

				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(1521);
			match(AS);
			setState(1522);
			match(IDENTITY);
			setState(1531);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LP) {
				{
				setState(1523);
				match(LP);
				setState(1525); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1524);
					sequence_options();
					}
					}
					setState(1527); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << CACHE) | (1L << CYCLE) | (1L << INCREMENT))) != 0) || ((((_la - 69)) & ~0x3f) == 0 && ((1L << (_la - 69)) & ((1L << (MAXVALUE - 69)) | (1L << (MINVALUE - 69)) | (1L << (NO - 69)) | (1L << (START - 69)))) != 0) );
				setState(1529);
				match(RP);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sequence_optionsContext extends ParserRuleContext {
		public TerminalNode START() { return getToken(KVQLParser.START, 0); }
		public TerminalNode WITH() { return getToken(KVQLParser.WITH, 0); }
		public Signed_intContext signed_int() {
			return getRuleContext(Signed_intContext.class,0);
		}
		public TerminalNode INCREMENT() { return getToken(KVQLParser.INCREMENT, 0); }
		public TerminalNode BY() { return getToken(KVQLParser.BY, 0); }
		public TerminalNode MAXVALUE() { return getToken(KVQLParser.MAXVALUE, 0); }
		public TerminalNode NO() { return getToken(KVQLParser.NO, 0); }
		public TerminalNode MINVALUE() { return getToken(KVQLParser.MINVALUE, 0); }
		public TerminalNode CACHE() { return getToken(KVQLParser.CACHE, 0); }
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public TerminalNode CYCLE() { return getToken(KVQLParser.CYCLE, 0); }
		public Sequence_optionsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sequence_options; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSequence_options(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSequence_options(this);
		}
	}

	public final Sequence_optionsContext sequence_options() throws RecognitionException {
		Sequence_optionsContext _localctx = new Sequence_optionsContext(_ctx, getState());
		enterRule(_localctx, 266, RULE_sequence_options);
		try {
			setState(1554);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,152,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(1533);
				match(START);
				setState(1534);
				match(WITH);
				setState(1535);
				signed_int();
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				setState(1536);
				match(INCREMENT);
				setState(1537);
				match(BY);
				setState(1538);
				signed_int();
				}
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				{
				setState(1539);
				match(MAXVALUE);
				setState(1540);
				signed_int();
				}
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				{
				setState(1541);
				match(NO);
				setState(1542);
				match(MAXVALUE);
				}
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				{
				setState(1543);
				match(MINVALUE);
				setState(1544);
				signed_int();
				}
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				{
				setState(1545);
				match(NO);
				setState(1546);
				match(MINVALUE);
				}
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				{
				setState(1547);
				match(CACHE);
				setState(1548);
				match(INT);
				}
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				{
				setState(1549);
				match(NO);
				setState(1550);
				match(CACHE);
				}
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(1551);
				match(CYCLE);
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				{
				setState(1552);
				match(NO);
				setState(1553);
				match(CYCLE);
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Uuid_defContext extends ParserRuleContext {
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public TerminalNode UUID() { return getToken(KVQLParser.UUID, 0); }
		public TerminalNode GENERATED() { return getToken(KVQLParser.GENERATED, 0); }
		public TerminalNode BY() { return getToken(KVQLParser.BY, 0); }
		public TerminalNode DEFAULT() { return getToken(KVQLParser.DEFAULT, 0); }
		public Uuid_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_uuid_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterUuid_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitUuid_def(this);
		}
	}

	public final Uuid_defContext uuid_def() throws RecognitionException {
		Uuid_defContext _localctx = new Uuid_defContext(_ctx, getState());
		enterRule(_localctx, 268, RULE_uuid_def);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1556);
			match(AS);
			setState(1557);
			match(UUID);
			setState(1561);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==GENERATED) {
				{
				setState(1558);
				match(GENERATED);
				setState(1559);
				match(BY);
				setState(1560);
				match(DEFAULT);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Alter_table_statementContext extends ParserRuleContext {
		public TerminalNode ALTER() { return getToken(KVQLParser.ALTER, 0); }
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public Alter_defContext alter_def() {
			return getRuleContext(Alter_defContext.class,0);
		}
		public Alter_table_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alter_table_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAlter_table_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAlter_table_statement(this);
		}
	}

	public final Alter_table_statementContext alter_table_statement() throws RecognitionException {
		Alter_table_statementContext _localctx = new Alter_table_statementContext(_ctx, getState());
		enterRule(_localctx, 270, RULE_alter_table_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1563);
			match(ALTER);
			setState(1564);
			match(TABLE);
			setState(1565);
			table_name();
			setState(1566);
			alter_def();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Alter_defContext extends ParserRuleContext {
		public Alter_field_statementsContext alter_field_statements() {
			return getRuleContext(Alter_field_statementsContext.class,0);
		}
		public Ttl_defContext ttl_def() {
			return getRuleContext(Ttl_defContext.class,0);
		}
		public Add_region_defContext add_region_def() {
			return getRuleContext(Add_region_defContext.class,0);
		}
		public Drop_region_defContext drop_region_def() {
			return getRuleContext(Drop_region_defContext.class,0);
		}
		public Alter_defContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alter_def; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAlter_def(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAlter_def(this);
		}
	}

	public final Alter_defContext alter_def() throws RecognitionException {
		Alter_defContext _localctx = new Alter_defContext(_ctx, getState());
		enterRule(_localctx, 272, RULE_alter_def);
		try {
			setState(1572);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LP:
				enterOuterAlt(_localctx, 1);
				{
				setState(1568);
				alter_field_statements();
				}
				break;
			case USING:
				enterOuterAlt(_localctx, 2);
				{
				setState(1569);
				ttl_def();
				}
				break;
			case ADD:
				enterOuterAlt(_localctx, 3);
				{
				setState(1570);
				add_region_def();
				}
				break;
			case DROP:
				enterOuterAlt(_localctx, 4);
				{
				setState(1571);
				drop_region_def();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Alter_field_statementsContext extends ParserRuleContext {
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public List<Add_field_statementContext> add_field_statement() {
			return getRuleContexts(Add_field_statementContext.class);
		}
		public Add_field_statementContext add_field_statement(int i) {
			return getRuleContext(Add_field_statementContext.class,i);
		}
		public List<Drop_field_statementContext> drop_field_statement() {
			return getRuleContexts(Drop_field_statementContext.class);
		}
		public Drop_field_statementContext drop_field_statement(int i) {
			return getRuleContext(Drop_field_statementContext.class,i);
		}
		public List<Modify_field_statementContext> modify_field_statement() {
			return getRuleContexts(Modify_field_statementContext.class);
		}
		public Modify_field_statementContext modify_field_statement(int i) {
			return getRuleContext(Modify_field_statementContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Alter_field_statementsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alter_field_statements; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAlter_field_statements(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAlter_field_statements(this);
		}
	}

	public final Alter_field_statementsContext alter_field_statements() throws RecognitionException {
		Alter_field_statementsContext _localctx = new Alter_field_statementsContext(_ctx, getState());
		enterRule(_localctx, 274, RULE_alter_field_statements);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1574);
			match(LP);
			setState(1578);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ADD:
				{
				setState(1575);
				add_field_statement();
				}
				break;
			case DROP:
				{
				setState(1576);
				drop_field_statement();
				}
				break;
			case MODIFY:
				{
				setState(1577);
				modify_field_statement();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(1588);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1580);
				match(COMMA);
				setState(1584);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ADD:
					{
					setState(1581);
					add_field_statement();
					}
					break;
				case DROP:
					{
					setState(1582);
					drop_field_statement();
					}
					break;
				case MODIFY:
					{
					setState(1583);
					modify_field_statement();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				}
				setState(1590);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(1591);
			match(RP);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Add_field_statementContext extends ParserRuleContext {
		public TerminalNode ADD() { return getToken(KVQLParser.ADD, 0); }
		public Schema_pathContext schema_path() {
			return getRuleContext(Schema_pathContext.class,0);
		}
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public Default_defContext default_def() {
			return getRuleContext(Default_defContext.class,0);
		}
		public Identity_defContext identity_def() {
			return getRuleContext(Identity_defContext.class,0);
		}
		public Uuid_defContext uuid_def() {
			return getRuleContext(Uuid_defContext.class,0);
		}
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public Add_field_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_add_field_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAdd_field_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAdd_field_statement(this);
		}
	}

	public final Add_field_statementContext add_field_statement() throws RecognitionException {
		Add_field_statementContext _localctx = new Add_field_statementContext(_ctx, getState());
		enterRule(_localctx, 276, RULE_add_field_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1593);
			match(ADD);
			setState(1594);
			schema_path();
			setState(1595);
			type_def();
			setState(1599);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEFAULT:
			case NOT:
				{
				setState(1596);
				default_def();
				}
				break;
			case GENERATED:
				{
				setState(1597);
				identity_def();
				}
				break;
			case AS:
				{
				setState(1598);
				uuid_def();
				}
				break;
			case COMMENT:
			case COMMA:
			case RP:
				break;
			default:
				break;
			}
			setState(1602);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMENT) {
				{
				setState(1601);
				comment();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_field_statementContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public Schema_pathContext schema_path() {
			return getRuleContext(Schema_pathContext.class,0);
		}
		public Drop_field_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_field_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_field_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_field_statement(this);
		}
	}

	public final Drop_field_statementContext drop_field_statement() throws RecognitionException {
		Drop_field_statementContext _localctx = new Drop_field_statementContext(_ctx, getState());
		enterRule(_localctx, 278, RULE_drop_field_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1604);
			match(DROP);
			setState(1605);
			schema_path();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Modify_field_statementContext extends ParserRuleContext {
		public TerminalNode MODIFY() { return getToken(KVQLParser.MODIFY, 0); }
		public Schema_pathContext schema_path() {
			return getRuleContext(Schema_pathContext.class,0);
		}
		public Identity_defContext identity_def() {
			return getRuleContext(Identity_defContext.class,0);
		}
		public Uuid_defContext uuid_def() {
			return getRuleContext(Uuid_defContext.class,0);
		}
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode IDENTITY() { return getToken(KVQLParser.IDENTITY, 0); }
		public Type_defContext type_def() {
			return getRuleContext(Type_defContext.class,0);
		}
		public Default_defContext default_def() {
			return getRuleContext(Default_defContext.class,0);
		}
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public Modify_field_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modify_field_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterModify_field_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitModify_field_statement(this);
		}
	}

	public final Modify_field_statementContext modify_field_statement() throws RecognitionException {
		Modify_field_statementContext _localctx = new Modify_field_statementContext(_ctx, getState());
		enterRule(_localctx, 280, RULE_modify_field_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1607);
			match(MODIFY);
			setState(1608);
			schema_path();
			setState(1620);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case JSON:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
				{
				{
				setState(1609);
				type_def();
				setState(1611);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==DEFAULT || _la==NOT) {
					{
					setState(1610);
					default_def();
					}
				}

				setState(1614);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMENT) {
					{
					setState(1613);
					comment();
					}
				}

				}
				}
				break;
			case GENERATED:
				{
				setState(1616);
				identity_def();
				}
				break;
			case AS:
				{
				setState(1617);
				uuid_def();
				}
				break;
			case DROP:
				{
				setState(1618);
				match(DROP);
				setState(1619);
				match(IDENTITY);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Schema_pathContext extends ParserRuleContext {
		public Init_schema_path_stepContext init_schema_path_step() {
			return getRuleContext(Init_schema_path_stepContext.class,0);
		}
		public List<TerminalNode> DOT() { return getTokens(KVQLParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(KVQLParser.DOT, i);
		}
		public List<Schema_path_stepContext> schema_path_step() {
			return getRuleContexts(Schema_path_stepContext.class);
		}
		public Schema_path_stepContext schema_path_step(int i) {
			return getRuleContext(Schema_path_stepContext.class,i);
		}
		public Schema_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_schema_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSchema_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSchema_path(this);
		}
	}

	public final Schema_pathContext schema_path() throws RecognitionException {
		Schema_pathContext _localctx = new Schema_pathContext(_ctx, getState());
		enterRule(_localctx, 282, RULE_schema_path);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1622);
			init_schema_path_step();
			setState(1627);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(1623);
				match(DOT);
				setState(1624);
				schema_path_step();
				}
				}
				setState(1629);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Init_schema_path_stepContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public List<TerminalNode> LBRACK() { return getTokens(KVQLParser.LBRACK); }
		public TerminalNode LBRACK(int i) {
			return getToken(KVQLParser.LBRACK, i);
		}
		public List<TerminalNode> RBRACK() { return getTokens(KVQLParser.RBRACK); }
		public TerminalNode RBRACK(int i) {
			return getToken(KVQLParser.RBRACK, i);
		}
		public Init_schema_path_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_init_schema_path_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterInit_schema_path_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitInit_schema_path_step(this);
		}
	}

	public final Init_schema_path_stepContext init_schema_path_step() throws RecognitionException {
		Init_schema_path_stepContext _localctx = new Init_schema_path_stepContext(_ctx, getState());
		enterRule(_localctx, 284, RULE_init_schema_path_step);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1630);
			id();
			setState(1635);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACK) {
				{
				{
				setState(1631);
				match(LBRACK);
				setState(1632);
				match(RBRACK);
				}
				}
				setState(1637);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Schema_path_stepContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public List<TerminalNode> LBRACK() { return getTokens(KVQLParser.LBRACK); }
		public TerminalNode LBRACK(int i) {
			return getToken(KVQLParser.LBRACK, i);
		}
		public List<TerminalNode> RBRACK() { return getTokens(KVQLParser.RBRACK); }
		public TerminalNode RBRACK(int i) {
			return getToken(KVQLParser.RBRACK, i);
		}
		public TerminalNode VALUES() { return getToken(KVQLParser.VALUES, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Schema_path_stepContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_schema_path_step; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSchema_path_step(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSchema_path_step(this);
		}
	}

	public final Schema_path_stepContext schema_path_step() throws RecognitionException {
		Schema_path_stepContext _localctx = new Schema_path_stepContext(_ctx, getState());
		enterRule(_localctx, 286, RULE_schema_path_step);
		int _la;
		try {
			setState(1649);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,166,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1638);
				id();
				setState(1643);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==LBRACK) {
					{
					{
					setState(1639);
					match(LBRACK);
					setState(1640);
					match(RBRACK);
					}
					}
					setState(1645);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1646);
				match(VALUES);
				setState(1647);
				match(LP);
				setState(1648);
				match(RP);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_table_statementContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public Drop_table_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_table_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_table_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_table_statement(this);
		}
	}

	public final Drop_table_statementContext drop_table_statement() throws RecognitionException {
		Drop_table_statementContext _localctx = new Drop_table_statementContext(_ctx, getState());
		enterRule(_localctx, 288, RULE_drop_table_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1651);
			match(DROP);
			setState(1652);
			match(TABLE);
			setState(1655);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,167,_ctx) ) {
			case 1:
				{
				setState(1653);
				match(IF);
				setState(1654);
				match(EXISTS);
				}
				break;
			}
			setState(1657);
			table_name();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_index_statementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode INDEX() { return getToken(KVQLParser.INDEX, 0); }
		public Index_nameContext index_name() {
			return getRuleContext(Index_nameContext.class,0);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Index_path_listContext index_path_list() {
			return getRuleContext(Index_path_listContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public TerminalNode WITH() { return getToken(KVQLParser.WITH, 0); }
		public TerminalNode NULLS() { return getToken(KVQLParser.NULLS, 0); }
		public TerminalNode NO() { return getToken(KVQLParser.NO, 0); }
		public Create_index_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_index_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_index_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_index_statement(this);
		}
	}

	public final Create_index_statementContext create_index_statement() throws RecognitionException {
		Create_index_statementContext _localctx = new Create_index_statementContext(_ctx, getState());
		enterRule(_localctx, 290, RULE_create_index_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1659);
			match(CREATE);
			setState(1660);
			match(INDEX);
			setState(1664);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,168,_ctx) ) {
			case 1:
				{
				setState(1661);
				match(IF);
				setState(1662);
				match(NOT);
				setState(1663);
				match(EXISTS);
				}
				break;
			}
			setState(1666);
			index_name();
			setState(1667);
			match(ON);
			setState(1668);
			table_name();
			setState(1683);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,171,_ctx) ) {
			case 1:
				{
				{
				setState(1669);
				match(LP);
				setState(1670);
				index_path_list();
				setState(1671);
				match(RP);
				setState(1677);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==WITH) {
					{
					setState(1672);
					match(WITH);
					setState(1674);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==NO) {
						{
						setState(1673);
						match(NO);
						}
					}

					setState(1676);
					match(NULLS);
					}
				}

				}
				}
				break;
			case 2:
				{
				{
				setState(1679);
				match(LP);
				setState(1680);
				index_path_list();
				 notifyErrorListeners("Missing closing ')'"); 
				}
				}
				break;
			}
			setState(1686);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMENT) {
				{
				setState(1685);
				comment();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Index_nameContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Index_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_index_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIndex_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIndex_name(this);
		}
	}

	public final Index_nameContext index_name() throws RecognitionException {
		Index_nameContext _localctx = new Index_nameContext(_ctx, getState());
		enterRule(_localctx, 292, RULE_index_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1688);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Index_path_listContext extends ParserRuleContext {
		public List<Index_pathContext> index_path() {
			return getRuleContexts(Index_pathContext.class);
		}
		public Index_pathContext index_path(int i) {
			return getRuleContext(Index_pathContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Index_path_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_index_path_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIndex_path_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIndex_path_list(this);
		}
	}

	public final Index_path_listContext index_path_list() throws RecognitionException {
		Index_path_listContext _localctx = new Index_path_listContext(_ctx, getState());
		enterRule(_localctx, 294, RULE_index_path_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1690);
			index_path();
			setState(1695);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1691);
				match(COMMA);
				setState(1692);
				index_path();
				}
				}
				setState(1697);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Index_pathContext extends ParserRuleContext {
		public Name_pathContext name_path() {
			return getRuleContext(Name_pathContext.class,0);
		}
		public Path_typeContext path_type() {
			return getRuleContext(Path_typeContext.class,0);
		}
		public Keys_exprContext keys_expr() {
			return getRuleContext(Keys_exprContext.class,0);
		}
		public Values_exprContext values_expr() {
			return getRuleContext(Values_exprContext.class,0);
		}
		public Index_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_index_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIndex_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIndex_path(this);
		}
	}

	public final Index_pathContext index_path() throws RecognitionException {
		Index_pathContext _localctx = new Index_pathContext(_ctx, getState());
		enterRule(_localctx, 296, RULE_index_path);
		int _la;
		try {
			setState(1707);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,176,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1698);
				name_path();
				setState(1700);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(1699);
					path_type();
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1702);
				keys_expr();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1703);
				values_expr();
				setState(1705);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(1704);
					path_type();
					}
				}

				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Keys_exprContext extends ParserRuleContext {
		public Name_pathContext name_path() {
			return getRuleContext(Name_pathContext.class,0);
		}
		public TerminalNode DOT() { return getToken(KVQLParser.DOT, 0); }
		public TerminalNode KEYS() { return getToken(KVQLParser.KEYS, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public TerminalNode KEYOF() { return getToken(KVQLParser.KEYOF, 0); }
		public Keys_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_keys_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterKeys_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitKeys_expr(this);
		}
	}

	public final Keys_exprContext keys_expr() throws RecognitionException {
		Keys_exprContext _localctx = new Keys_exprContext(_ctx, getState());
		enterRule(_localctx, 298, RULE_keys_expr);
		try {
			setState(1725);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,177,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1709);
				name_path();
				setState(1710);
				match(DOT);
				setState(1711);
				match(KEYS);
				setState(1712);
				match(LP);
				setState(1713);
				match(RP);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1715);
				match(KEYOF);
				setState(1716);
				match(LP);
				setState(1717);
				name_path();
				setState(1718);
				match(RP);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1720);
				match(KEYS);
				setState(1721);
				match(LP);
				setState(1722);
				name_path();
				setState(1723);
				match(RP);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Values_exprContext extends ParserRuleContext {
		public List<TerminalNode> DOT() { return getTokens(KVQLParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(KVQLParser.DOT, i);
		}
		public List<Name_pathContext> name_path() {
			return getRuleContexts(Name_pathContext.class);
		}
		public Name_pathContext name_path(int i) {
			return getRuleContext(Name_pathContext.class,i);
		}
		public TerminalNode VALUES() { return getToken(KVQLParser.VALUES, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public TerminalNode LBRACK() { return getToken(KVQLParser.LBRACK, 0); }
		public TerminalNode RBRACK() { return getToken(KVQLParser.RBRACK, 0); }
		public TerminalNode ELEMENTOF() { return getToken(KVQLParser.ELEMENTOF, 0); }
		public Values_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_values_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterValues_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitValues_expr(this);
		}
	}

	public final Values_exprContext values_expr() throws RecognitionException {
		Values_exprContext _localctx = new Values_exprContext(_ctx, getState());
		enterRule(_localctx, 300, RULE_values_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1742);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,178,_ctx) ) {
			case 1:
				{
				{
				setState(1727);
				name_path();
				setState(1728);
				match(DOT);
				setState(1729);
				match(VALUES);
				setState(1730);
				match(LP);
				setState(1731);
				match(RP);
				}
				}
				break;
			case 2:
				{
				{
				setState(1733);
				name_path();
				setState(1734);
				match(LBRACK);
				setState(1735);
				match(RBRACK);
				}
				}
				break;
			case 3:
				{
				{
				setState(1737);
				match(ELEMENTOF);
				setState(1738);
				match(LP);
				setState(1739);
				name_path();
				setState(1740);
				match(RP);
				}
				}
				break;
			}
			setState(1746);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DOT) {
				{
				setState(1744);
				match(DOT);
				setState(1745);
				name_path();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Path_typeContext extends ParserRuleContext {
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public TerminalNode INTEGER_T() { return getToken(KVQLParser.INTEGER_T, 0); }
		public TerminalNode LONG_T() { return getToken(KVQLParser.LONG_T, 0); }
		public TerminalNode DOUBLE_T() { return getToken(KVQLParser.DOUBLE_T, 0); }
		public TerminalNode STRING_T() { return getToken(KVQLParser.STRING_T, 0); }
		public TerminalNode BOOLEAN_T() { return getToken(KVQLParser.BOOLEAN_T, 0); }
		public TerminalNode NUMBER_T() { return getToken(KVQLParser.NUMBER_T, 0); }
		public TerminalNode ANYATOMIC_T() { return getToken(KVQLParser.ANYATOMIC_T, 0); }
		public TerminalNode POINT_T() { return getToken(KVQLParser.POINT_T, 0); }
		public TerminalNode GEOMETRY_T() { return getToken(KVQLParser.GEOMETRY_T, 0); }
		public JsobjectContext jsobject() {
			return getRuleContext(JsobjectContext.class,0);
		}
		public Path_typeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_path_type; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPath_type(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPath_type(this);
		}
	}

	public final Path_typeContext path_type() throws RecognitionException {
		Path_typeContext _localctx = new Path_typeContext(_ctx, getState());
		enterRule(_localctx, 302, RULE_path_type);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1748);
			match(AS);
			setState(1761);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INTEGER_T:
				{
				setState(1749);
				match(INTEGER_T);
				}
				break;
			case LONG_T:
				{
				setState(1750);
				match(LONG_T);
				}
				break;
			case DOUBLE_T:
				{
				setState(1751);
				match(DOUBLE_T);
				}
				break;
			case STRING_T:
				{
				setState(1752);
				match(STRING_T);
				}
				break;
			case BOOLEAN_T:
				{
				setState(1753);
				match(BOOLEAN_T);
				}
				break;
			case NUMBER_T:
				{
				setState(1754);
				match(NUMBER_T);
				}
				break;
			case ANYATOMIC_T:
				{
				setState(1755);
				match(ANYATOMIC_T);
				}
				break;
			case GEOMETRY_T:
				{
				{
				setState(1756);
				match(GEOMETRY_T);
				setState(1758);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,180,_ctx) ) {
				case 1:
					{
					setState(1757);
					jsobject();
					}
					break;
				}
				}
				}
				break;
			case POINT_T:
				{
				setState(1760);
				match(POINT_T);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_text_index_statementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode FULLTEXT() { return getToken(KVQLParser.FULLTEXT, 0); }
		public TerminalNode INDEX() { return getToken(KVQLParser.INDEX, 0); }
		public Index_nameContext index_name() {
			return getRuleContext(Index_nameContext.class,0);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public Fts_field_listContext fts_field_list() {
			return getRuleContext(Fts_field_listContext.class,0);
		}
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public Es_propertiesContext es_properties() {
			return getRuleContext(Es_propertiesContext.class,0);
		}
		public TerminalNode OVERRIDE() { return getToken(KVQLParser.OVERRIDE, 0); }
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public Create_text_index_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_text_index_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_text_index_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_text_index_statement(this);
		}
	}

	public final Create_text_index_statementContext create_text_index_statement() throws RecognitionException {
		Create_text_index_statementContext _localctx = new Create_text_index_statementContext(_ctx, getState());
		enterRule(_localctx, 304, RULE_create_text_index_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1763);
			match(CREATE);
			setState(1764);
			match(FULLTEXT);
			setState(1765);
			match(INDEX);
			setState(1769);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,182,_ctx) ) {
			case 1:
				{
				setState(1766);
				match(IF);
				setState(1767);
				match(NOT);
				setState(1768);
				match(EXISTS);
				}
				break;
			}
			setState(1771);
			index_name();
			setState(1772);
			match(ON);
			setState(1773);
			table_name();
			setState(1774);
			fts_field_list();
			setState(1776);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ES_SHARDS || _la==ES_REPLICAS) {
				{
				setState(1775);
				es_properties();
				}
			}

			setState(1779);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OVERRIDE) {
				{
				setState(1778);
				match(OVERRIDE);
				}
			}

			setState(1782);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMENT) {
				{
				setState(1781);
				comment();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Fts_field_listContext extends ParserRuleContext {
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Fts_path_listContext fts_path_list() {
			return getRuleContext(Fts_path_listContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Fts_field_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fts_field_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFts_field_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFts_field_list(this);
		}
	}

	public final Fts_field_listContext fts_field_list() throws RecognitionException {
		Fts_field_listContext _localctx = new Fts_field_listContext(_ctx, getState());
		enterRule(_localctx, 306, RULE_fts_field_list);
		try {
			setState(1792);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,186,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1784);
				match(LP);
				setState(1785);
				fts_path_list();
				setState(1786);
				match(RP);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1788);
				match(LP);
				setState(1789);
				fts_path_list();
				notifyErrorListeners("Missing closing ')'");
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Fts_path_listContext extends ParserRuleContext {
		public List<Fts_pathContext> fts_path() {
			return getRuleContexts(Fts_pathContext.class);
		}
		public Fts_pathContext fts_path(int i) {
			return getRuleContext(Fts_pathContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Fts_path_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fts_path_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFts_path_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFts_path_list(this);
		}
	}

	public final Fts_path_listContext fts_path_list() throws RecognitionException {
		Fts_path_listContext _localctx = new Fts_path_listContext(_ctx, getState());
		enterRule(_localctx, 308, RULE_fts_path_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1794);
			fts_path();
			setState(1799);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1795);
				match(COMMA);
				setState(1796);
				fts_path();
				}
				}
				setState(1801);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Fts_pathContext extends ParserRuleContext {
		public Index_pathContext index_path() {
			return getRuleContext(Index_pathContext.class,0);
		}
		public JsobjectContext jsobject() {
			return getRuleContext(JsobjectContext.class,0);
		}
		public Fts_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fts_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterFts_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitFts_path(this);
		}
	}

	public final Fts_pathContext fts_path() throws RecognitionException {
		Fts_pathContext _localctx = new Fts_pathContext(_ctx, getState());
		enterRule(_localctx, 310, RULE_fts_path);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1802);
			index_path();
			setState(1804);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LBRACE) {
				{
				setState(1803);
				jsobject();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Es_propertiesContext extends ParserRuleContext {
		public List<Es_property_assignmentContext> es_property_assignment() {
			return getRuleContexts(Es_property_assignmentContext.class);
		}
		public Es_property_assignmentContext es_property_assignment(int i) {
			return getRuleContext(Es_property_assignmentContext.class,i);
		}
		public Es_propertiesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_es_properties; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterEs_properties(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitEs_properties(this);
		}
	}

	public final Es_propertiesContext es_properties() throws RecognitionException {
		Es_propertiesContext _localctx = new Es_propertiesContext(_ctx, getState());
		enterRule(_localctx, 312, RULE_es_properties);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1806);
			es_property_assignment();
			setState(1810);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ES_SHARDS || _la==ES_REPLICAS) {
				{
				{
				setState(1807);
				es_property_assignment();
				}
				}
				setState(1812);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Es_property_assignmentContext extends ParserRuleContext {
		public TerminalNode ES_SHARDS() { return getToken(KVQLParser.ES_SHARDS, 0); }
		public TerminalNode EQ() { return getToken(KVQLParser.EQ, 0); }
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public TerminalNode ES_REPLICAS() { return getToken(KVQLParser.ES_REPLICAS, 0); }
		public Es_property_assignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_es_property_assignment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterEs_property_assignment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitEs_property_assignment(this);
		}
	}

	public final Es_property_assignmentContext es_property_assignment() throws RecognitionException {
		Es_property_assignmentContext _localctx = new Es_property_assignmentContext(_ctx, getState());
		enterRule(_localctx, 314, RULE_es_property_assignment);
		try {
			setState(1819);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ES_SHARDS:
				enterOuterAlt(_localctx, 1);
				{
				setState(1813);
				match(ES_SHARDS);
				setState(1814);
				match(EQ);
				setState(1815);
				match(INT);
				}
				break;
			case ES_REPLICAS:
				enterOuterAlt(_localctx, 2);
				{
				setState(1816);
				match(ES_REPLICAS);
				setState(1817);
				match(EQ);
				setState(1818);
				match(INT);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_index_statementContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode INDEX() { return getToken(KVQLParser.INDEX, 0); }
		public Index_nameContext index_name() {
			return getRuleContext(Index_nameContext.class,0);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public TerminalNode OVERRIDE() { return getToken(KVQLParser.OVERRIDE, 0); }
		public Drop_index_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_index_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_index_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_index_statement(this);
		}
	}

	public final Drop_index_statementContext drop_index_statement() throws RecognitionException {
		Drop_index_statementContext _localctx = new Drop_index_statementContext(_ctx, getState());
		enterRule(_localctx, 316, RULE_drop_index_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1821);
			match(DROP);
			setState(1822);
			match(INDEX);
			setState(1825);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,191,_ctx) ) {
			case 1:
				{
				setState(1823);
				match(IF);
				setState(1824);
				match(EXISTS);
				}
				break;
			}
			setState(1827);
			index_name();
			setState(1828);
			match(ON);
			setState(1829);
			table_name();
			setState(1831);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OVERRIDE) {
				{
				setState(1830);
				match(OVERRIDE);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Describe_statementContext extends ParserRuleContext {
		public TerminalNode DESCRIBE() { return getToken(KVQLParser.DESCRIBE, 0); }
		public TerminalNode DESC() { return getToken(KVQLParser.DESC, 0); }
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public TerminalNode INDEX() { return getToken(KVQLParser.INDEX, 0); }
		public Index_nameContext index_name() {
			return getRuleContext(Index_nameContext.class,0);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public TerminalNode JSON() { return getToken(KVQLParser.JSON, 0); }
		public TerminalNode LP() { return getToken(KVQLParser.LP, 0); }
		public Schema_path_listContext schema_path_list() {
			return getRuleContext(Schema_path_listContext.class,0);
		}
		public TerminalNode RP() { return getToken(KVQLParser.RP, 0); }
		public Describe_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_describe_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDescribe_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDescribe_statement(this);
		}
	}

	public final Describe_statementContext describe_statement() throws RecognitionException {
		Describe_statementContext _localctx = new Describe_statementContext(_ctx, getState());
		enterRule(_localctx, 318, RULE_describe_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1833);
			_la = _input.LA(1);
			if ( !(_la==DESC || _la==DESCRIBE) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(1836);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(1834);
				match(AS);
				setState(1835);
				match(JSON);
				}
			}

			setState(1855);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TABLE:
				{
				setState(1838);
				match(TABLE);
				{
				setState(1839);
				table_name();
				}
				setState(1848);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,194,_ctx) ) {
				case 1:
					{
					{
					setState(1840);
					match(LP);
					setState(1841);
					schema_path_list();
					setState(1842);
					match(RP);
					}
					}
					break;
				case 2:
					{
					{
					setState(1844);
					match(LP);
					setState(1845);
					schema_path_list();
					 notifyErrorListeners("Missing closing ')'")
					             ; 
					}
					}
					break;
				}
				}
				break;
			case INDEX:
				{
				setState(1850);
				match(INDEX);
				setState(1851);
				index_name();
				setState(1852);
				match(ON);
				setState(1853);
				table_name();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Schema_path_listContext extends ParserRuleContext {
		public List<Schema_pathContext> schema_path() {
			return getRuleContexts(Schema_pathContext.class);
		}
		public Schema_pathContext schema_path(int i) {
			return getRuleContext(Schema_pathContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Schema_path_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_schema_path_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSchema_path_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSchema_path_list(this);
		}
	}

	public final Schema_path_listContext schema_path_list() throws RecognitionException {
		Schema_path_listContext _localctx = new Schema_path_listContext(_ctx, getState());
		enterRule(_localctx, 320, RULE_schema_path_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1857);
			schema_path();
			setState(1862);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(1858);
				match(COMMA);
				setState(1859);
				schema_path();
				}
				}
				setState(1864);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Show_statementContext extends ParserRuleContext {
		public TerminalNode SHOW() { return getToken(KVQLParser.SHOW, 0); }
		public TerminalNode TABLES() { return getToken(KVQLParser.TABLES, 0); }
		public TerminalNode USERS() { return getToken(KVQLParser.USERS, 0); }
		public TerminalNode ROLES() { return getToken(KVQLParser.ROLES, 0); }
		public TerminalNode USER() { return getToken(KVQLParser.USER, 0); }
		public Identifier_or_stringContext identifier_or_string() {
			return getRuleContext(Identifier_or_stringContext.class,0);
		}
		public TerminalNode ROLE() { return getToken(KVQLParser.ROLE, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public TerminalNode INDEXES() { return getToken(KVQLParser.INDEXES, 0); }
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public TerminalNode NAMESPACES() { return getToken(KVQLParser.NAMESPACES, 0); }
		public TerminalNode REGIONS() { return getToken(KVQLParser.REGIONS, 0); }
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public TerminalNode JSON() { return getToken(KVQLParser.JSON, 0); }
		public Show_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_show_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterShow_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitShow_statement(this);
		}
	}

	public final Show_statementContext show_statement() throws RecognitionException {
		Show_statementContext _localctx = new Show_statementContext(_ctx, getState());
		enterRule(_localctx, 322, RULE_show_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1865);
			match(SHOW);
			setState(1868);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(1866);
				match(AS);
				setState(1867);
				match(JSON);
				}
			}

			setState(1884);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TABLES:
				{
				setState(1870);
				match(TABLES);
				}
				break;
			case USERS:
				{
				setState(1871);
				match(USERS);
				}
				break;
			case ROLES:
				{
				setState(1872);
				match(ROLES);
				}
				break;
			case USER:
				{
				setState(1873);
				match(USER);
				setState(1874);
				identifier_or_string();
				}
				break;
			case ROLE:
				{
				setState(1875);
				match(ROLE);
				setState(1876);
				id();
				}
				break;
			case INDEXES:
				{
				setState(1877);
				match(INDEXES);
				setState(1878);
				match(ON);
				setState(1879);
				table_name();
				}
				break;
			case TABLE:
				{
				setState(1880);
				match(TABLE);
				setState(1881);
				table_name();
				}
				break;
			case NAMESPACES:
				{
				setState(1882);
				match(NAMESPACES);
				}
				break;
			case REGIONS:
				{
				setState(1883);
				match(REGIONS);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_user_statementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode USER() { return getToken(KVQLParser.USER, 0); }
		public Create_user_identified_clauseContext create_user_identified_clause() {
			return getRuleContext(Create_user_identified_clauseContext.class,0);
		}
		public Account_lockContext account_lock() {
			return getRuleContext(Account_lockContext.class,0);
		}
		public TerminalNode ADMIN() { return getToken(KVQLParser.ADMIN, 0); }
		public Create_user_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_user_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_user_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_user_statement(this);
		}
	}

	public final Create_user_statementContext create_user_statement() throws RecognitionException {
		Create_user_statementContext _localctx = new Create_user_statementContext(_ctx, getState());
		enterRule(_localctx, 324, RULE_create_user_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1886);
			match(CREATE);
			setState(1887);
			match(USER);
			setState(1888);
			create_user_identified_clause();
			setState(1890);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ACCOUNT) {
				{
				setState(1889);
				account_lock();
				}
			}

			setState(1893);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ADMIN) {
				{
				setState(1892);
				match(ADMIN);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_role_statementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode ROLE() { return getToken(KVQLParser.ROLE, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Create_role_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_role_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_role_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_role_statement(this);
		}
	}

	public final Create_role_statementContext create_role_statement() throws RecognitionException {
		Create_role_statementContext _localctx = new Create_role_statementContext(_ctx, getState());
		enterRule(_localctx, 326, RULE_create_role_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1895);
			match(CREATE);
			setState(1896);
			match(ROLE);
			setState(1897);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Alter_user_statementContext extends ParserRuleContext {
		public TerminalNode ALTER() { return getToken(KVQLParser.ALTER, 0); }
		public TerminalNode USER() { return getToken(KVQLParser.USER, 0); }
		public Identifier_or_stringContext identifier_or_string() {
			return getRuleContext(Identifier_or_stringContext.class,0);
		}
		public Reset_password_clauseContext reset_password_clause() {
			return getRuleContext(Reset_password_clauseContext.class,0);
		}
		public TerminalNode CLEAR_RETAINED_PASSWORD() { return getToken(KVQLParser.CLEAR_RETAINED_PASSWORD, 0); }
		public TerminalNode PASSWORD_EXPIRE() { return getToken(KVQLParser.PASSWORD_EXPIRE, 0); }
		public Password_lifetimeContext password_lifetime() {
			return getRuleContext(Password_lifetimeContext.class,0);
		}
		public Account_lockContext account_lock() {
			return getRuleContext(Account_lockContext.class,0);
		}
		public Alter_user_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alter_user_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAlter_user_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAlter_user_statement(this);
		}
	}

	public final Alter_user_statementContext alter_user_statement() throws RecognitionException {
		Alter_user_statementContext _localctx = new Alter_user_statementContext(_ctx, getState());
		enterRule(_localctx, 328, RULE_alter_user_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1899);
			match(ALTER);
			setState(1900);
			match(USER);
			setState(1901);
			identifier_or_string();
			setState(1903);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IDENTIFIED) {
				{
				setState(1902);
				reset_password_clause();
				}
			}

			setState(1906);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==CLEAR_RETAINED_PASSWORD) {
				{
				setState(1905);
				match(CLEAR_RETAINED_PASSWORD);
				}
			}

			setState(1909);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==PASSWORD_EXPIRE) {
				{
				setState(1908);
				match(PASSWORD_EXPIRE);
				}
			}

			setState(1912);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==PASSWORD) {
				{
				setState(1911);
				password_lifetime();
				}
			}

			setState(1915);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ACCOUNT) {
				{
				setState(1914);
				account_lock();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_user_statementContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode USER() { return getToken(KVQLParser.USER, 0); }
		public Identifier_or_stringContext identifier_or_string() {
			return getRuleContext(Identifier_or_stringContext.class,0);
		}
		public TerminalNode CASCADE() { return getToken(KVQLParser.CASCADE, 0); }
		public Drop_user_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_user_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_user_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_user_statement(this);
		}
	}

	public final Drop_user_statementContext drop_user_statement() throws RecognitionException {
		Drop_user_statementContext _localctx = new Drop_user_statementContext(_ctx, getState());
		enterRule(_localctx, 330, RULE_drop_user_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1917);
			match(DROP);
			setState(1918);
			match(USER);
			setState(1919);
			identifier_or_string();
			setState(1921);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==CASCADE) {
				{
				setState(1920);
				match(CASCADE);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Drop_role_statementContext extends ParserRuleContext {
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode ROLE() { return getToken(KVQLParser.ROLE, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Drop_role_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_drop_role_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDrop_role_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDrop_role_statement(this);
		}
	}

	public final Drop_role_statementContext drop_role_statement() throws RecognitionException {
		Drop_role_statementContext _localctx = new Drop_role_statementContext(_ctx, getState());
		enterRule(_localctx, 332, RULE_drop_role_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1923);
			match(DROP);
			setState(1924);
			match(ROLE);
			setState(1925);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Grant_statementContext extends ParserRuleContext {
		public TerminalNode GRANT() { return getToken(KVQLParser.GRANT, 0); }
		public Grant_rolesContext grant_roles() {
			return getRuleContext(Grant_rolesContext.class,0);
		}
		public Grant_system_privilegesContext grant_system_privileges() {
			return getRuleContext(Grant_system_privilegesContext.class,0);
		}
		public Grant_object_privilegesContext grant_object_privileges() {
			return getRuleContext(Grant_object_privilegesContext.class,0);
		}
		public Grant_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_grant_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterGrant_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitGrant_statement(this);
		}
	}

	public final Grant_statementContext grant_statement() throws RecognitionException {
		Grant_statementContext _localctx = new Grant_statementContext(_ctx, getState());
		enterRule(_localctx, 334, RULE_grant_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1927);
			match(GRANT);
			setState(1931);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,207,_ctx) ) {
			case 1:
				{
				setState(1928);
				grant_roles();
				}
				break;
			case 2:
				{
				setState(1929);
				grant_system_privileges();
				}
				break;
			case 3:
				{
				setState(1930);
				grant_object_privileges();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Revoke_statementContext extends ParserRuleContext {
		public TerminalNode REVOKE() { return getToken(KVQLParser.REVOKE, 0); }
		public Revoke_rolesContext revoke_roles() {
			return getRuleContext(Revoke_rolesContext.class,0);
		}
		public Revoke_system_privilegesContext revoke_system_privileges() {
			return getRuleContext(Revoke_system_privilegesContext.class,0);
		}
		public Revoke_object_privilegesContext revoke_object_privileges() {
			return getRuleContext(Revoke_object_privilegesContext.class,0);
		}
		public Revoke_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_revoke_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRevoke_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRevoke_statement(this);
		}
	}

	public final Revoke_statementContext revoke_statement() throws RecognitionException {
		Revoke_statementContext _localctx = new Revoke_statementContext(_ctx, getState());
		enterRule(_localctx, 336, RULE_revoke_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1933);
			match(REVOKE);
			setState(1937);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,208,_ctx) ) {
			case 1:
				{
				setState(1934);
				revoke_roles();
				}
				break;
			case 2:
				{
				setState(1935);
				revoke_system_privileges();
				}
				break;
			case 3:
				{
				setState(1936);
				revoke_object_privileges();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Identifier_or_stringContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public Identifier_or_stringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier_or_string; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIdentifier_or_string(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIdentifier_or_string(this);
		}
	}

	public final Identifier_or_stringContext identifier_or_string() throws RecognitionException {
		Identifier_or_stringContext _localctx = new Identifier_or_stringContext(_ctx, getState());
		enterRule(_localctx, 338, RULE_identifier_or_string);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1941);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case RDIV:
			case ID:
			case BAD_ID:
				{
				setState(1939);
				id();
				}
				break;
			case DSTRING:
			case STRING:
				{
				setState(1940);
				string();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Identified_clauseContext extends ParserRuleContext {
		public TerminalNode IDENTIFIED() { return getToken(KVQLParser.IDENTIFIED, 0); }
		public By_passwordContext by_password() {
			return getRuleContext(By_passwordContext.class,0);
		}
		public Identified_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identified_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterIdentified_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitIdentified_clause(this);
		}
	}

	public final Identified_clauseContext identified_clause() throws RecognitionException {
		Identified_clauseContext _localctx = new Identified_clauseContext(_ctx, getState());
		enterRule(_localctx, 340, RULE_identified_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1943);
			match(IDENTIFIED);
			setState(1944);
			by_password();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Create_user_identified_clauseContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Identified_clauseContext identified_clause() {
			return getRuleContext(Identified_clauseContext.class,0);
		}
		public TerminalNode PASSWORD_EXPIRE() { return getToken(KVQLParser.PASSWORD_EXPIRE, 0); }
		public Password_lifetimeContext password_lifetime() {
			return getRuleContext(Password_lifetimeContext.class,0);
		}
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public TerminalNode IDENTIFIED_EXTERNALLY() { return getToken(KVQLParser.IDENTIFIED_EXTERNALLY, 0); }
		public Create_user_identified_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_create_user_identified_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterCreate_user_identified_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitCreate_user_identified_clause(this);
		}
	}

	public final Create_user_identified_clauseContext create_user_identified_clause() throws RecognitionException {
		Create_user_identified_clauseContext _localctx = new Create_user_identified_clauseContext(_ctx, getState());
		enterRule(_localctx, 342, RULE_create_user_identified_clause);
		int _la;
		try {
			setState(1957);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case RDIV:
			case ID:
			case BAD_ID:
				enterOuterAlt(_localctx, 1);
				{
				setState(1946);
				id();
				setState(1947);
				identified_clause();
				setState(1949);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==PASSWORD_EXPIRE) {
					{
					setState(1948);
					match(PASSWORD_EXPIRE);
					}
				}

				setState(1952);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==PASSWORD) {
					{
					setState(1951);
					password_lifetime();
					}
				}

				}
				break;
			case DSTRING:
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(1954);
				string();
				setState(1955);
				match(IDENTIFIED_EXTERNALLY);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class By_passwordContext extends ParserRuleContext {
		public TerminalNode BY() { return getToken(KVQLParser.BY, 0); }
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public By_passwordContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_by_password; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterBy_password(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitBy_password(this);
		}
	}

	public final By_passwordContext by_password() throws RecognitionException {
		By_passwordContext _localctx = new By_passwordContext(_ctx, getState());
		enterRule(_localctx, 344, RULE_by_password);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1959);
			match(BY);
			setState(1960);
			string();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Password_lifetimeContext extends ParserRuleContext {
		public TerminalNode PASSWORD() { return getToken(KVQLParser.PASSWORD, 0); }
		public TerminalNode LIFETIME() { return getToken(KVQLParser.LIFETIME, 0); }
		public DurationContext duration() {
			return getRuleContext(DurationContext.class,0);
		}
		public Password_lifetimeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_password_lifetime; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPassword_lifetime(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPassword_lifetime(this);
		}
	}

	public final Password_lifetimeContext password_lifetime() throws RecognitionException {
		Password_lifetimeContext _localctx = new Password_lifetimeContext(_ctx, getState());
		enterRule(_localctx, 346, RULE_password_lifetime);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1962);
			match(PASSWORD);
			setState(1963);
			match(LIFETIME);
			setState(1964);
			duration();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Reset_password_clauseContext extends ParserRuleContext {
		public Identified_clauseContext identified_clause() {
			return getRuleContext(Identified_clauseContext.class,0);
		}
		public TerminalNode RETAIN_CURRENT_PASSWORD() { return getToken(KVQLParser.RETAIN_CURRENT_PASSWORD, 0); }
		public Reset_password_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_reset_password_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterReset_password_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitReset_password_clause(this);
		}
	}

	public final Reset_password_clauseContext reset_password_clause() throws RecognitionException {
		Reset_password_clauseContext _localctx = new Reset_password_clauseContext(_ctx, getState());
		enterRule(_localctx, 348, RULE_reset_password_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1966);
			identified_clause();
			setState(1968);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==RETAIN_CURRENT_PASSWORD) {
				{
				setState(1967);
				match(RETAIN_CURRENT_PASSWORD);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Account_lockContext extends ParserRuleContext {
		public TerminalNode ACCOUNT() { return getToken(KVQLParser.ACCOUNT, 0); }
		public TerminalNode LOCK() { return getToken(KVQLParser.LOCK, 0); }
		public TerminalNode UNLOCK() { return getToken(KVQLParser.UNLOCK, 0); }
		public Account_lockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_account_lock; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterAccount_lock(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitAccount_lock(this);
		}
	}

	public final Account_lockContext account_lock() throws RecognitionException {
		Account_lockContext _localctx = new Account_lockContext(_ctx, getState());
		enterRule(_localctx, 350, RULE_account_lock);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1970);
			match(ACCOUNT);
			setState(1971);
			_la = _input.LA(1);
			if ( !(_la==LOCK || _la==UNLOCK) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Grant_rolesContext extends ParserRuleContext {
		public Id_listContext id_list() {
			return getRuleContext(Id_listContext.class,0);
		}
		public TerminalNode TO() { return getToken(KVQLParser.TO, 0); }
		public PrincipalContext principal() {
			return getRuleContext(PrincipalContext.class,0);
		}
		public Grant_rolesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_grant_roles; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterGrant_roles(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitGrant_roles(this);
		}
	}

	public final Grant_rolesContext grant_roles() throws RecognitionException {
		Grant_rolesContext _localctx = new Grant_rolesContext(_ctx, getState());
		enterRule(_localctx, 352, RULE_grant_roles);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1973);
			id_list();
			setState(1974);
			match(TO);
			setState(1975);
			principal();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Grant_system_privilegesContext extends ParserRuleContext {
		public Sys_priv_listContext sys_priv_list() {
			return getRuleContext(Sys_priv_listContext.class,0);
		}
		public TerminalNode TO() { return getToken(KVQLParser.TO, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Grant_system_privilegesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_grant_system_privileges; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterGrant_system_privileges(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitGrant_system_privileges(this);
		}
	}

	public final Grant_system_privilegesContext grant_system_privileges() throws RecognitionException {
		Grant_system_privilegesContext _localctx = new Grant_system_privilegesContext(_ctx, getState());
		enterRule(_localctx, 354, RULE_grant_system_privileges);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1977);
			sys_priv_list();
			setState(1978);
			match(TO);
			setState(1979);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Grant_object_privilegesContext extends ParserRuleContext {
		public Obj_priv_listContext obj_priv_list() {
			return getRuleContext(Obj_priv_listContext.class,0);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public TerminalNode TO() { return getToken(KVQLParser.TO, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public ObjectContext object() {
			return getRuleContext(ObjectContext.class,0);
		}
		public TerminalNode NAMESPACE() { return getToken(KVQLParser.NAMESPACE, 0); }
		public NamespaceContext namespace() {
			return getRuleContext(NamespaceContext.class,0);
		}
		public Grant_object_privilegesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_grant_object_privileges; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterGrant_object_privileges(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitGrant_object_privileges(this);
		}
	}

	public final Grant_object_privilegesContext grant_object_privileges() throws RecognitionException {
		Grant_object_privilegesContext _localctx = new Grant_object_privilegesContext(_ctx, getState());
		enterRule(_localctx, 356, RULE_grant_object_privileges);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1981);
			obj_priv_list();
			setState(1982);
			match(ON);
			setState(1986);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,214,_ctx) ) {
			case 1:
				{
				setState(1983);
				object();
				}
				break;
			case 2:
				{
				setState(1984);
				match(NAMESPACE);
				setState(1985);
				namespace();
				}
				break;
			}
			setState(1988);
			match(TO);
			setState(1989);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Revoke_rolesContext extends ParserRuleContext {
		public Id_listContext id_list() {
			return getRuleContext(Id_listContext.class,0);
		}
		public TerminalNode FROM() { return getToken(KVQLParser.FROM, 0); }
		public PrincipalContext principal() {
			return getRuleContext(PrincipalContext.class,0);
		}
		public Revoke_rolesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_revoke_roles; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRevoke_roles(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRevoke_roles(this);
		}
	}

	public final Revoke_rolesContext revoke_roles() throws RecognitionException {
		Revoke_rolesContext _localctx = new Revoke_rolesContext(_ctx, getState());
		enterRule(_localctx, 358, RULE_revoke_roles);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1991);
			id_list();
			setState(1992);
			match(FROM);
			setState(1993);
			principal();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Revoke_system_privilegesContext extends ParserRuleContext {
		public Sys_priv_listContext sys_priv_list() {
			return getRuleContext(Sys_priv_listContext.class,0);
		}
		public TerminalNode FROM() { return getToken(KVQLParser.FROM, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Revoke_system_privilegesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_revoke_system_privileges; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRevoke_system_privileges(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRevoke_system_privileges(this);
		}
	}

	public final Revoke_system_privilegesContext revoke_system_privileges() throws RecognitionException {
		Revoke_system_privilegesContext _localctx = new Revoke_system_privilegesContext(_ctx, getState());
		enterRule(_localctx, 360, RULE_revoke_system_privileges);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1995);
			sys_priv_list();
			setState(1996);
			match(FROM);
			setState(1997);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Revoke_object_privilegesContext extends ParserRuleContext {
		public Obj_priv_listContext obj_priv_list() {
			return getRuleContext(Obj_priv_listContext.class,0);
		}
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public TerminalNode FROM() { return getToken(KVQLParser.FROM, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public ObjectContext object() {
			return getRuleContext(ObjectContext.class,0);
		}
		public TerminalNode NAMESPACE() { return getToken(KVQLParser.NAMESPACE, 0); }
		public NamespaceContext namespace() {
			return getRuleContext(NamespaceContext.class,0);
		}
		public Revoke_object_privilegesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_revoke_object_privileges; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterRevoke_object_privileges(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitRevoke_object_privileges(this);
		}
	}

	public final Revoke_object_privilegesContext revoke_object_privileges() throws RecognitionException {
		Revoke_object_privilegesContext _localctx = new Revoke_object_privilegesContext(_ctx, getState());
		enterRule(_localctx, 362, RULE_revoke_object_privileges);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1999);
			obj_priv_list();
			setState(2000);
			match(ON);
			setState(2004);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,215,_ctx) ) {
			case 1:
				{
				setState(2001);
				object();
				}
				break;
			case 2:
				{
				setState(2002);
				match(NAMESPACE);
				setState(2003);
				namespace();
				}
				break;
			}
			setState(2006);
			match(FROM);
			setState(2007);
			id();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PrincipalContext extends ParserRuleContext {
		public TerminalNode USER() { return getToken(KVQLParser.USER, 0); }
		public Identifier_or_stringContext identifier_or_string() {
			return getRuleContext(Identifier_or_stringContext.class,0);
		}
		public TerminalNode ROLE() { return getToken(KVQLParser.ROLE, 0); }
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public PrincipalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_principal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPrincipal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPrincipal(this);
		}
	}

	public final PrincipalContext principal() throws RecognitionException {
		PrincipalContext _localctx = new PrincipalContext(_ctx, getState());
		enterRule(_localctx, 364, RULE_principal);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2013);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case USER:
				{
				setState(2009);
				match(USER);
				setState(2010);
				identifier_or_string();
				}
				break;
			case ROLE:
				{
				setState(2011);
				match(ROLE);
				setState(2012);
				id();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sys_priv_listContext extends ParserRuleContext {
		public List<Priv_itemContext> priv_item() {
			return getRuleContexts(Priv_itemContext.class);
		}
		public Priv_itemContext priv_item(int i) {
			return getRuleContext(Priv_itemContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Sys_priv_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sys_priv_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSys_priv_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSys_priv_list(this);
		}
	}

	public final Sys_priv_listContext sys_priv_list() throws RecognitionException {
		Sys_priv_listContext _localctx = new Sys_priv_listContext(_ctx, getState());
		enterRule(_localctx, 366, RULE_sys_priv_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2015);
			priv_item();
			setState(2020);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(2016);
				match(COMMA);
				setState(2017);
				priv_item();
				}
				}
				setState(2022);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Priv_itemContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public TerminalNode ALL_PRIVILEGES() { return getToken(KVQLParser.ALL_PRIVILEGES, 0); }
		public Priv_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_priv_item; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterPriv_item(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitPriv_item(this);
		}
	}

	public final Priv_itemContext priv_item() throws RecognitionException {
		Priv_itemContext _localctx = new Priv_itemContext(_ctx, getState());
		enterRule(_localctx, 368, RULE_priv_item);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2025);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case RDIV:
			case ID:
			case BAD_ID:
				{
				setState(2023);
				id();
				}
				break;
			case ALL_PRIVILEGES:
				{
				setState(2024);
				match(ALL_PRIVILEGES);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Obj_priv_listContext extends ParserRuleContext {
		public List<Priv_itemContext> priv_item() {
			return getRuleContexts(Priv_itemContext.class);
		}
		public Priv_itemContext priv_item(int i) {
			return getRuleContext(Priv_itemContext.class,i);
		}
		public List<TerminalNode> ALL() { return getTokens(KVQLParser.ALL); }
		public TerminalNode ALL(int i) {
			return getToken(KVQLParser.ALL, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Obj_priv_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_obj_priv_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterObj_priv_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitObj_priv_list(this);
		}
	}

	public final Obj_priv_listContext obj_priv_list() throws RecognitionException {
		Obj_priv_listContext _localctx = new Obj_priv_listContext(_ctx, getState());
		enterRule(_localctx, 370, RULE_obj_priv_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2029);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,219,_ctx) ) {
			case 1:
				{
				setState(2027);
				priv_item();
				}
				break;
			case 2:
				{
				setState(2028);
				match(ALL);
				}
				break;
			}
			setState(2038);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(2031);
				match(COMMA);
				setState(2034);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,220,_ctx) ) {
				case 1:
					{
					setState(2032);
					priv_item();
					}
					break;
				case 2:
					{
					setState(2033);
					match(ALL);
					}
					break;
				}
				}
				}
				setState(2040);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ObjectContext extends ParserRuleContext {
		public Table_nameContext table_name() {
			return getRuleContext(Table_nameContext.class,0);
		}
		public ObjectContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_object; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterObject(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitObject(this);
		}
	}

	public final ObjectContext object() throws RecognitionException {
		ObjectContext _localctx = new ObjectContext(_ctx, getState());
		enterRule(_localctx, 372, RULE_object);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2041);
			table_name();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Json_textContext extends ParserRuleContext {
		public JsobjectContext jsobject() {
			return getRuleContext(JsobjectContext.class,0);
		}
		public JsarrayContext jsarray() {
			return getRuleContext(JsarrayContext.class,0);
		}
		public Json_textContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_json_text; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJson_text(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJson_text(this);
		}
	}

	public final Json_textContext json_text() throws RecognitionException {
		Json_textContext _localctx = new Json_textContext(_ctx, getState());
		enterRule(_localctx, 374, RULE_json_text);
		try {
			setState(2045);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LBRACE:
				enterOuterAlt(_localctx, 1);
				{
				setState(2043);
				jsobject();
				}
				break;
			case LBRACK:
				enterOuterAlt(_localctx, 2);
				{
				setState(2044);
				jsarray();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class JsobjectContext extends ParserRuleContext {
		public JsobjectContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jsobject; }
	 
		public JsobjectContext() { }
		public void copyFrom(JsobjectContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class JsonObjectContext extends JsobjectContext {
		public TerminalNode LBRACE() { return getToken(KVQLParser.LBRACE, 0); }
		public List<JspairContext> jspair() {
			return getRuleContexts(JspairContext.class);
		}
		public JspairContext jspair(int i) {
			return getRuleContext(JspairContext.class,i);
		}
		public TerminalNode RBRACE() { return getToken(KVQLParser.RBRACE, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public JsonObjectContext(JsobjectContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJsonObject(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJsonObject(this);
		}
	}
	public static class EmptyJsonObjectContext extends JsobjectContext {
		public TerminalNode LBRACE() { return getToken(KVQLParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(KVQLParser.RBRACE, 0); }
		public EmptyJsonObjectContext(JsobjectContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterEmptyJsonObject(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitEmptyJsonObject(this);
		}
	}

	public final JsobjectContext jsobject() throws RecognitionException {
		JsobjectContext _localctx = new JsobjectContext(_ctx, getState());
		enterRule(_localctx, 376, RULE_jsobject);
		int _la;
		try {
			setState(2060);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,224,_ctx) ) {
			case 1:
				_localctx = new JsonObjectContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(2047);
				match(LBRACE);
				setState(2048);
				jspair();
				setState(2053);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(2049);
					match(COMMA);
					setState(2050);
					jspair();
					}
					}
					setState(2055);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(2056);
				match(RBRACE);
				}
				break;
			case 2:
				_localctx = new EmptyJsonObjectContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(2058);
				match(LBRACE);
				setState(2059);
				match(RBRACE);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class JsarrayContext extends ParserRuleContext {
		public JsarrayContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jsarray; }
	 
		public JsarrayContext() { }
		public void copyFrom(JsarrayContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class EmptyJsonArrayContext extends JsarrayContext {
		public TerminalNode LBRACK() { return getToken(KVQLParser.LBRACK, 0); }
		public TerminalNode RBRACK() { return getToken(KVQLParser.RBRACK, 0); }
		public EmptyJsonArrayContext(JsarrayContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterEmptyJsonArray(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitEmptyJsonArray(this);
		}
	}
	public static class ArrayOfJsonValuesContext extends JsarrayContext {
		public TerminalNode LBRACK() { return getToken(KVQLParser.LBRACK, 0); }
		public List<JsvalueContext> jsvalue() {
			return getRuleContexts(JsvalueContext.class);
		}
		public JsvalueContext jsvalue(int i) {
			return getRuleContext(JsvalueContext.class,i);
		}
		public TerminalNode RBRACK() { return getToken(KVQLParser.RBRACK, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public ArrayOfJsonValuesContext(JsarrayContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterArrayOfJsonValues(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitArrayOfJsonValues(this);
		}
	}

	public final JsarrayContext jsarray() throws RecognitionException {
		JsarrayContext _localctx = new JsarrayContext(_ctx, getState());
		enterRule(_localctx, 378, RULE_jsarray);
		int _la;
		try {
			setState(2075);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,226,_ctx) ) {
			case 1:
				_localctx = new ArrayOfJsonValuesContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(2062);
				match(LBRACK);
				setState(2063);
				jsvalue();
				setState(2068);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(2064);
					match(COMMA);
					setState(2065);
					jsvalue();
					}
					}
					setState(2070);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(2071);
				match(RBRACK);
				}
				break;
			case 2:
				_localctx = new EmptyJsonArrayContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(2073);
				match(LBRACK);
				setState(2074);
				match(RBRACK);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class JspairContext extends ParserRuleContext {
		public JspairContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jspair; }
	 
		public JspairContext() { }
		public void copyFrom(JspairContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class JsonPairContext extends JspairContext {
		public TerminalNode DSTRING() { return getToken(KVQLParser.DSTRING, 0); }
		public TerminalNode COLON() { return getToken(KVQLParser.COLON, 0); }
		public JsvalueContext jsvalue() {
			return getRuleContext(JsvalueContext.class,0);
		}
		public JsonPairContext(JspairContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJsonPair(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJsonPair(this);
		}
	}

	public final JspairContext jspair() throws RecognitionException {
		JspairContext _localctx = new JspairContext(_ctx, getState());
		enterRule(_localctx, 380, RULE_jspair);
		try {
			_localctx = new JsonPairContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(2077);
			match(DSTRING);
			setState(2078);
			match(COLON);
			setState(2079);
			jsvalue();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class JsvalueContext extends ParserRuleContext {
		public JsvalueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jsvalue; }
	 
		public JsvalueContext() { }
		public void copyFrom(JsvalueContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class JsonAtomContext extends JsvalueContext {
		public TerminalNode DSTRING() { return getToken(KVQLParser.DSTRING, 0); }
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public TerminalNode TRUE() { return getToken(KVQLParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(KVQLParser.FALSE, 0); }
		public TerminalNode NULL() { return getToken(KVQLParser.NULL, 0); }
		public JsonAtomContext(JsvalueContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJsonAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJsonAtom(this);
		}
	}
	public static class JsonArrayValueContext extends JsvalueContext {
		public JsarrayContext jsarray() {
			return getRuleContext(JsarrayContext.class,0);
		}
		public JsonArrayValueContext(JsvalueContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJsonArrayValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJsonArrayValue(this);
		}
	}
	public static class JsonObjectValueContext extends JsvalueContext {
		public JsobjectContext jsobject() {
			return getRuleContext(JsobjectContext.class,0);
		}
		public JsonObjectValueContext(JsvalueContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterJsonObjectValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitJsonObjectValue(this);
		}
	}

	public final JsvalueContext jsvalue() throws RecognitionException {
		JsvalueContext _localctx = new JsvalueContext(_ctx, getState());
		enterRule(_localctx, 382, RULE_jsvalue);
		try {
			setState(2088);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LBRACE:
				_localctx = new JsonObjectValueContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(2081);
				jsobject();
				}
				break;
			case LBRACK:
				_localctx = new JsonArrayValueContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(2082);
				jsarray();
				}
				break;
			case DSTRING:
				_localctx = new JsonAtomContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(2083);
				match(DSTRING);
				}
				break;
			case MINUS:
			case INT:
			case FLOAT:
			case NUMBER:
				_localctx = new JsonAtomContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(2084);
				number();
				}
				break;
			case TRUE:
				_localctx = new JsonAtomContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(2085);
				match(TRUE);
				}
				break;
			case FALSE:
				_localctx = new JsonAtomContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(2086);
				match(FALSE);
				}
				break;
			case NULL:
				_localctx = new JsonAtomContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(2087);
				match(NULL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CommentContext extends ParserRuleContext {
		public TerminalNode COMMENT() { return getToken(KVQLParser.COMMENT, 0); }
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public CommentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterComment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitComment(this);
		}
	}

	public final CommentContext comment() throws RecognitionException {
		CommentContext _localctx = new CommentContext(_ctx, getState());
		enterRule(_localctx, 384, RULE_comment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2090);
			match(COMMENT);
			setState(2091);
			string();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DurationContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public Time_unitContext time_unit() {
			return getRuleContext(Time_unitContext.class,0);
		}
		public DurationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_duration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterDuration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitDuration(this);
		}
	}

	public final DurationContext duration() throws RecognitionException {
		DurationContext _localctx = new DurationContext(_ctx, getState());
		enterRule(_localctx, 386, RULE_duration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2093);
			match(INT);
			setState(2094);
			time_unit();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Time_unitContext extends ParserRuleContext {
		public TerminalNode SECONDS() { return getToken(KVQLParser.SECONDS, 0); }
		public TerminalNode MINUTES() { return getToken(KVQLParser.MINUTES, 0); }
		public TerminalNode HOURS() { return getToken(KVQLParser.HOURS, 0); }
		public TerminalNode DAYS() { return getToken(KVQLParser.DAYS, 0); }
		public Time_unitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_time_unit; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterTime_unit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitTime_unit(this);
		}
	}

	public final Time_unitContext time_unit() throws RecognitionException {
		Time_unitContext _localctx = new Time_unitContext(_ctx, getState());
		enterRule(_localctx, 388, RULE_time_unit);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2096);
			_la = _input.LA(1);
			if ( !(_la==DAYS || _la==HOURS || _la==MINUTES || _la==SECONDS) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NumberContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public TerminalNode FLOAT() { return getToken(KVQLParser.FLOAT, 0); }
		public TerminalNode NUMBER() { return getToken(KVQLParser.NUMBER, 0); }
		public TerminalNode MINUS() { return getToken(KVQLParser.MINUS, 0); }
		public NumberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_number; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterNumber(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitNumber(this);
		}
	}

	public final NumberContext number() throws RecognitionException {
		NumberContext _localctx = new NumberContext(_ctx, getState());
		enterRule(_localctx, 390, RULE_number);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2099);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==MINUS) {
				{
				setState(2098);
				match(MINUS);
				}
			}

			setState(2101);
			_la = _input.LA(1);
			if ( !(((((_la - 182)) & ~0x3f) == 0 && ((1L << (_la - 182)) & ((1L << (INT - 182)) | (1L << (FLOAT - 182)) | (1L << (NUMBER - 182)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Signed_intContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(KVQLParser.INT, 0); }
		public TerminalNode MINUS() { return getToken(KVQLParser.MINUS, 0); }
		public TerminalNode PLUS() { return getToken(KVQLParser.PLUS, 0); }
		public Signed_intContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_signed_int; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterSigned_int(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitSigned_int(this);
		}
	}

	public final Signed_intContext signed_int() throws RecognitionException {
		Signed_intContext _localctx = new Signed_intContext(_ctx, getState());
		enterRule(_localctx, 392, RULE_signed_int);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2104);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==PLUS || _la==MINUS) {
				{
				setState(2103);
				_la = _input.LA(1);
				if ( !(_la==PLUS || _la==MINUS) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
			}

			setState(2106);
			match(INT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StringContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(KVQLParser.STRING, 0); }
		public TerminalNode DSTRING() { return getToken(KVQLParser.DSTRING, 0); }
		public StringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_string; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterString(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitString(this);
		}
	}

	public final StringContext string() throws RecognitionException {
		StringContext _localctx = new StringContext(_ctx, getState());
		enterRule(_localctx, 394, RULE_string);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(2108);
			_la = _input.LA(1);
			if ( !(_la==DSTRING || _la==STRING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Id_listContext extends ParserRuleContext {
		public List<IdContext> id() {
			return getRuleContexts(IdContext.class);
		}
		public IdContext id(int i) {
			return getRuleContext(IdContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KVQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KVQLParser.COMMA, i);
		}
		public Id_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_id_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterId_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitId_list(this);
		}
	}

	public final Id_listContext id_list() throws RecognitionException {
		Id_listContext _localctx = new Id_listContext(_ctx, getState());
		enterRule(_localctx, 396, RULE_id_list);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(2110);
			id();
			setState(2115);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,230,_ctx);
			while ( _alt!=2 && _alt!=oracle.kv.shaded.org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(2111);
					match(COMMA);
					setState(2112);
					id();
					}
					} 
				}
				setState(2117);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,230,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IdContext extends ParserRuleContext {
		public TerminalNode ACCOUNT() { return getToken(KVQLParser.ACCOUNT, 0); }
		public TerminalNode ADD() { return getToken(KVQLParser.ADD, 0); }
		public TerminalNode ADMIN() { return getToken(KVQLParser.ADMIN, 0); }
		public TerminalNode ALL() { return getToken(KVQLParser.ALL, 0); }
		public TerminalNode ALTER() { return getToken(KVQLParser.ALTER, 0); }
		public TerminalNode ALWAYS() { return getToken(KVQLParser.ALWAYS, 0); }
		public TerminalNode ANCESTORS() { return getToken(KVQLParser.ANCESTORS, 0); }
		public TerminalNode AND() { return getToken(KVQLParser.AND, 0); }
		public TerminalNode ANY_T() { return getToken(KVQLParser.ANY_T, 0); }
		public TerminalNode ANYATOMIC_T() { return getToken(KVQLParser.ANYATOMIC_T, 0); }
		public TerminalNode ANYJSONATOMIC_T() { return getToken(KVQLParser.ANYJSONATOMIC_T, 0); }
		public TerminalNode ANYRECORD_T() { return getToken(KVQLParser.ANYRECORD_T, 0); }
		public TerminalNode AS() { return getToken(KVQLParser.AS, 0); }
		public TerminalNode ASC() { return getToken(KVQLParser.ASC, 0); }
		public TerminalNode BY() { return getToken(KVQLParser.BY, 0); }
		public TerminalNode CACHE() { return getToken(KVQLParser.CACHE, 0); }
		public TerminalNode CASE() { return getToken(KVQLParser.CASE, 0); }
		public TerminalNode CAST() { return getToken(KVQLParser.CAST, 0); }
		public TerminalNode COMMENT() { return getToken(KVQLParser.COMMENT, 0); }
		public TerminalNode COUNT() { return getToken(KVQLParser.COUNT, 0); }
		public TerminalNode CREATE() { return getToken(KVQLParser.CREATE, 0); }
		public TerminalNode CYCLE() { return getToken(KVQLParser.CYCLE, 0); }
		public TerminalNode DAYS() { return getToken(KVQLParser.DAYS, 0); }
		public TerminalNode DECLARE() { return getToken(KVQLParser.DECLARE, 0); }
		public TerminalNode DEFAULT() { return getToken(KVQLParser.DEFAULT, 0); }
		public TerminalNode DELETE() { return getToken(KVQLParser.DELETE, 0); }
		public TerminalNode DESC() { return getToken(KVQLParser.DESC, 0); }
		public TerminalNode DESCENDANTS() { return getToken(KVQLParser.DESCENDANTS, 0); }
		public TerminalNode DESCRIBE() { return getToken(KVQLParser.DESCRIBE, 0); }
		public TerminalNode DISTINCT() { return getToken(KVQLParser.DISTINCT, 0); }
		public TerminalNode DROP() { return getToken(KVQLParser.DROP, 0); }
		public TerminalNode ELEMENTOF() { return getToken(KVQLParser.ELEMENTOF, 0); }
		public TerminalNode ELSE() { return getToken(KVQLParser.ELSE, 0); }
		public TerminalNode END() { return getToken(KVQLParser.END, 0); }
		public TerminalNode ES_SHARDS() { return getToken(KVQLParser.ES_SHARDS, 0); }
		public TerminalNode ES_REPLICAS() { return getToken(KVQLParser.ES_REPLICAS, 0); }
		public TerminalNode EXISTS() { return getToken(KVQLParser.EXISTS, 0); }
		public TerminalNode EXTRACT() { return getToken(KVQLParser.EXTRACT, 0); }
		public TerminalNode FIRST() { return getToken(KVQLParser.FIRST, 0); }
		public TerminalNode FROM() { return getToken(KVQLParser.FROM, 0); }
		public TerminalNode FULLTEXT() { return getToken(KVQLParser.FULLTEXT, 0); }
		public TerminalNode GENERATED() { return getToken(KVQLParser.GENERATED, 0); }
		public TerminalNode GRANT() { return getToken(KVQLParser.GRANT, 0); }
		public TerminalNode GROUP() { return getToken(KVQLParser.GROUP, 0); }
		public TerminalNode HOURS() { return getToken(KVQLParser.HOURS, 0); }
		public TerminalNode IDENTIFIED() { return getToken(KVQLParser.IDENTIFIED, 0); }
		public TerminalNode IDENTITY() { return getToken(KVQLParser.IDENTITY, 0); }
		public TerminalNode IF() { return getToken(KVQLParser.IF, 0); }
		public TerminalNode INCREMENT() { return getToken(KVQLParser.INCREMENT, 0); }
		public TerminalNode INDEX() { return getToken(KVQLParser.INDEX, 0); }
		public TerminalNode INDEXES() { return getToken(KVQLParser.INDEXES, 0); }
		public TerminalNode INSERT() { return getToken(KVQLParser.INSERT, 0); }
		public TerminalNode INTO() { return getToken(KVQLParser.INTO, 0); }
		public TerminalNode IN() { return getToken(KVQLParser.IN, 0); }
		public TerminalNode IS() { return getToken(KVQLParser.IS, 0); }
		public TerminalNode JSON() { return getToken(KVQLParser.JSON, 0); }
		public TerminalNode KEY() { return getToken(KVQLParser.KEY, 0); }
		public TerminalNode KEYOF() { return getToken(KVQLParser.KEYOF, 0); }
		public TerminalNode KEYS() { return getToken(KVQLParser.KEYS, 0); }
		public TerminalNode LIFETIME() { return getToken(KVQLParser.LIFETIME, 0); }
		public TerminalNode LAST() { return getToken(KVQLParser.LAST, 0); }
		public TerminalNode LIMIT() { return getToken(KVQLParser.LIMIT, 0); }
		public TerminalNode LOCAL() { return getToken(KVQLParser.LOCAL, 0); }
		public TerminalNode LOCK() { return getToken(KVQLParser.LOCK, 0); }
		public TerminalNode MINUTES() { return getToken(KVQLParser.MINUTES, 0); }
		public TerminalNode MODIFY() { return getToken(KVQLParser.MODIFY, 0); }
		public TerminalNode NAMESPACE() { return getToken(KVQLParser.NAMESPACE, 0); }
		public TerminalNode NAMESPACES() { return getToken(KVQLParser.NAMESPACES, 0); }
		public TerminalNode NESTED() { return getToken(KVQLParser.NESTED, 0); }
		public TerminalNode NO() { return getToken(KVQLParser.NO, 0); }
		public TerminalNode NOT() { return getToken(KVQLParser.NOT, 0); }
		public TerminalNode NULLS() { return getToken(KVQLParser.NULLS, 0); }
		public TerminalNode OF() { return getToken(KVQLParser.OF, 0); }
		public TerminalNode OFFSET() { return getToken(KVQLParser.OFFSET, 0); }
		public TerminalNode ON() { return getToken(KVQLParser.ON, 0); }
		public TerminalNode OR() { return getToken(KVQLParser.OR, 0); }
		public TerminalNode ORDER() { return getToken(KVQLParser.ORDER, 0); }
		public TerminalNode OVERRIDE() { return getToken(KVQLParser.OVERRIDE, 0); }
		public TerminalNode PASSWORD() { return getToken(KVQLParser.PASSWORD, 0); }
		public TerminalNode PRIMARY() { return getToken(KVQLParser.PRIMARY, 0); }
		public TerminalNode PUT() { return getToken(KVQLParser.PUT, 0); }
		public TerminalNode RDIV() { return getToken(KVQLParser.RDIV, 0); }
		public TerminalNode REGION() { return getToken(KVQLParser.REGION, 0); }
		public TerminalNode REGIONS() { return getToken(KVQLParser.REGIONS, 0); }
		public TerminalNode REMOVE() { return getToken(KVQLParser.REMOVE, 0); }
		public TerminalNode RETURNING() { return getToken(KVQLParser.RETURNING, 0); }
		public TerminalNode ROLE() { return getToken(KVQLParser.ROLE, 0); }
		public TerminalNode ROLES() { return getToken(KVQLParser.ROLES, 0); }
		public TerminalNode REVOKE() { return getToken(KVQLParser.REVOKE, 0); }
		public TerminalNode SECONDS() { return getToken(KVQLParser.SECONDS, 0); }
		public TerminalNode SELECT() { return getToken(KVQLParser.SELECT, 0); }
		public TerminalNode SEQ_TRANSFORM() { return getToken(KVQLParser.SEQ_TRANSFORM, 0); }
		public TerminalNode SET() { return getToken(KVQLParser.SET, 0); }
		public TerminalNode SHARD() { return getToken(KVQLParser.SHARD, 0); }
		public TerminalNode SHOW() { return getToken(KVQLParser.SHOW, 0); }
		public TerminalNode START() { return getToken(KVQLParser.START, 0); }
		public TerminalNode TABLE() { return getToken(KVQLParser.TABLE, 0); }
		public TerminalNode TABLES() { return getToken(KVQLParser.TABLES, 0); }
		public TerminalNode THEN() { return getToken(KVQLParser.THEN, 0); }
		public TerminalNode TO() { return getToken(KVQLParser.TO, 0); }
		public TerminalNode TTL() { return getToken(KVQLParser.TTL, 0); }
		public TerminalNode TYPE() { return getToken(KVQLParser.TYPE, 0); }
		public TerminalNode UNLOCK() { return getToken(KVQLParser.UNLOCK, 0); }
		public TerminalNode UPDATE() { return getToken(KVQLParser.UPDATE, 0); }
		public TerminalNode UPSERT() { return getToken(KVQLParser.UPSERT, 0); }
		public TerminalNode USER() { return getToken(KVQLParser.USER, 0); }
		public TerminalNode USERS() { return getToken(KVQLParser.USERS, 0); }
		public TerminalNode USING() { return getToken(KVQLParser.USING, 0); }
		public TerminalNode VALUES() { return getToken(KVQLParser.VALUES, 0); }
		public TerminalNode WHEN() { return getToken(KVQLParser.WHEN, 0); }
		public TerminalNode WHERE() { return getToken(KVQLParser.WHERE, 0); }
		public TerminalNode WITH() { return getToken(KVQLParser.WITH, 0); }
		public TerminalNode ARRAY_T() { return getToken(KVQLParser.ARRAY_T, 0); }
		public TerminalNode BINARY_T() { return getToken(KVQLParser.BINARY_T, 0); }
		public TerminalNode BOOLEAN_T() { return getToken(KVQLParser.BOOLEAN_T, 0); }
		public TerminalNode DOUBLE_T() { return getToken(KVQLParser.DOUBLE_T, 0); }
		public TerminalNode ENUM_T() { return getToken(KVQLParser.ENUM_T, 0); }
		public TerminalNode FLOAT_T() { return getToken(KVQLParser.FLOAT_T, 0); }
		public TerminalNode GEOMETRY_T() { return getToken(KVQLParser.GEOMETRY_T, 0); }
		public TerminalNode LONG_T() { return getToken(KVQLParser.LONG_T, 0); }
		public TerminalNode INTEGER_T() { return getToken(KVQLParser.INTEGER_T, 0); }
		public TerminalNode MAP_T() { return getToken(KVQLParser.MAP_T, 0); }
		public TerminalNode NUMBER_T() { return getToken(KVQLParser.NUMBER_T, 0); }
		public TerminalNode POINT_T() { return getToken(KVQLParser.POINT_T, 0); }
		public TerminalNode RECORD_T() { return getToken(KVQLParser.RECORD_T, 0); }
		public TerminalNode STRING_T() { return getToken(KVQLParser.STRING_T, 0); }
		public TerminalNode TIMESTAMP_T() { return getToken(KVQLParser.TIMESTAMP_T, 0); }
		public TerminalNode SCALAR_T() { return getToken(KVQLParser.SCALAR_T, 0); }
		public TerminalNode ID() { return getToken(KVQLParser.ID, 0); }
		public TerminalNode BAD_ID() { return getToken(KVQLParser.BAD_ID, 0); }
		public IdContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_id; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).enterId(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KVQLListener ) ((KVQLListener)listener).exitId(this);
		}
	}

	public final IdContext id() throws RecognitionException {
		IdContext _localctx = new IdContext(_ctx, getState());
		enterRule(_localctx, 398, RULE_id);
		int _la;
		try {
			setState(2121);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ACCOUNT:
			case ADD:
			case ADMIN:
			case ALL:
			case ALTER:
			case ALWAYS:
			case ANCESTORS:
			case AND:
			case AS:
			case ASC:
			case BY:
			case CACHE:
			case CASE:
			case CAST:
			case COMMENT:
			case COUNT:
			case CREATE:
			case CYCLE:
			case DAYS:
			case DECLARE:
			case DEFAULT:
			case DELETE:
			case DESC:
			case DESCENDANTS:
			case DESCRIBE:
			case DISTINCT:
			case DROP:
			case ELEMENTOF:
			case ELSE:
			case END:
			case ES_SHARDS:
			case ES_REPLICAS:
			case EXISTS:
			case EXTRACT:
			case FIRST:
			case FROM:
			case FULLTEXT:
			case GENERATED:
			case GRANT:
			case GROUP:
			case HOURS:
			case IDENTIFIED:
			case IDENTITY:
			case IF:
			case IN:
			case INCREMENT:
			case INDEX:
			case INDEXES:
			case INSERT:
			case INTO:
			case IS:
			case JSON:
			case KEY:
			case KEYOF:
			case KEYS:
			case LAST:
			case LIFETIME:
			case LIMIT:
			case LOCAL:
			case LOCK:
			case MINUTES:
			case MODIFY:
			case NAMESPACE:
			case NAMESPACES:
			case NESTED:
			case NO:
			case NOT:
			case NULLS:
			case OFFSET:
			case OF:
			case ON:
			case OR:
			case ORDER:
			case OVERRIDE:
			case PASSWORD:
			case PRIMARY:
			case PUT:
			case REGION:
			case REGIONS:
			case REMOVE:
			case RETURNING:
			case REVOKE:
			case ROLE:
			case ROLES:
			case SECONDS:
			case SELECT:
			case SEQ_TRANSFORM:
			case SET:
			case SHARD:
			case SHOW:
			case START:
			case TABLE:
			case TABLES:
			case THEN:
			case TO:
			case TTL:
			case TYPE:
			case UNLOCK:
			case UPDATE:
			case UPSERT:
			case USER:
			case USERS:
			case USING:
			case VALUES:
			case WHEN:
			case WHERE:
			case WITH:
			case ARRAY_T:
			case BINARY_T:
			case BOOLEAN_T:
			case DOUBLE_T:
			case ENUM_T:
			case FLOAT_T:
			case GEOMETRY_T:
			case INTEGER_T:
			case LONG_T:
			case MAP_T:
			case NUMBER_T:
			case POINT_T:
			case RECORD_T:
			case STRING_T:
			case TIMESTAMP_T:
			case ANY_T:
			case ANYATOMIC_T:
			case ANYJSONATOMIC_T:
			case ANYRECORD_T:
			case SCALAR_T:
			case RDIV:
			case ID:
				enterOuterAlt(_localctx, 1);
				{
				setState(2118);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ACCOUNT) | (1L << ADD) | (1L << ADMIN) | (1L << ALL) | (1L << ALTER) | (1L << ALWAYS) | (1L << ANCESTORS) | (1L << AND) | (1L << AS) | (1L << ASC) | (1L << BY) | (1L << CACHE) | (1L << CASE) | (1L << CAST) | (1L << COMMENT) | (1L << COUNT) | (1L << CREATE) | (1L << CYCLE) | (1L << DAYS) | (1L << DECLARE) | (1L << DEFAULT) | (1L << DELETE) | (1L << DESC) | (1L << DESCENDANTS) | (1L << DESCRIBE) | (1L << DISTINCT) | (1L << DROP) | (1L << ELEMENTOF) | (1L << ELSE) | (1L << END) | (1L << ES_SHARDS) | (1L << ES_REPLICAS) | (1L << EXISTS) | (1L << EXTRACT) | (1L << FIRST) | (1L << FROM) | (1L << FULLTEXT) | (1L << GENERATED) | (1L << GRANT) | (1L << GROUP) | (1L << HOURS) | (1L << IDENTIFIED) | (1L << IDENTITY) | (1L << IF) | (1L << IN) | (1L << INCREMENT) | (1L << INDEX) | (1L << INDEXES) | (1L << INSERT) | (1L << INTO) | (1L << IS) | (1L << JSON) | (1L << KEY) | (1L << KEYOF) | (1L << KEYS) | (1L << LAST))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (LIFETIME - 65)) | (1L << (LIMIT - 65)) | (1L << (LOCAL - 65)) | (1L << (LOCK - 65)) | (1L << (MINUTES - 65)) | (1L << (MODIFY - 65)) | (1L << (NAMESPACE - 65)) | (1L << (NAMESPACES - 65)) | (1L << (NESTED - 65)) | (1L << (NO - 65)) | (1L << (NOT - 65)) | (1L << (NULLS - 65)) | (1L << (OFFSET - 65)) | (1L << (OF - 65)) | (1L << (ON - 65)) | (1L << (OR - 65)) | (1L << (ORDER - 65)) | (1L << (OVERRIDE - 65)) | (1L << (PASSWORD - 65)) | (1L << (PRIMARY - 65)) | (1L << (PUT - 65)) | (1L << (REGION - 65)) | (1L << (REGIONS - 65)) | (1L << (REMOVE - 65)) | (1L << (RETURNING - 65)) | (1L << (REVOKE - 65)) | (1L << (ROLE - 65)) | (1L << (ROLES - 65)) | (1L << (SECONDS - 65)) | (1L << (SELECT - 65)) | (1L << (SEQ_TRANSFORM - 65)) | (1L << (SET - 65)) | (1L << (SHARD - 65)) | (1L << (SHOW - 65)) | (1L << (START - 65)) | (1L << (TABLE - 65)) | (1L << (TABLES - 65)) | (1L << (THEN - 65)) | (1L << (TO - 65)) | (1L << (TTL - 65)) | (1L << (TYPE - 65)) | (1L << (UNLOCK - 65)) | (1L << (UPDATE - 65)) | (1L << (UPSERT - 65)) | (1L << (USER - 65)) | (1L << (USERS - 65)) | (1L << (USING - 65)) | (1L << (VALUES - 65)) | (1L << (WHEN - 65)) | (1L << (WHERE - 65)) | (1L << (WITH - 65)))) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & ((1L << (ARRAY_T - 129)) | (1L << (BINARY_T - 129)) | (1L << (BOOLEAN_T - 129)) | (1L << (DOUBLE_T - 129)) | (1L << (ENUM_T - 129)) | (1L << (FLOAT_T - 129)) | (1L << (GEOMETRY_T - 129)) | (1L << (INTEGER_T - 129)) | (1L << (LONG_T - 129)) | (1L << (MAP_T - 129)) | (1L << (NUMBER_T - 129)) | (1L << (POINT_T - 129)) | (1L << (RECORD_T - 129)) | (1L << (STRING_T - 129)) | (1L << (TIMESTAMP_T - 129)) | (1L << (ANY_T - 129)) | (1L << (ANYATOMIC_T - 129)) | (1L << (ANYJSONATOMIC_T - 129)) | (1L << (ANYRECORD_T - 129)) | (1L << (SCALAR_T - 129)) | (1L << (RDIV - 129)) | (1L << (ID - 129)))) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case BAD_ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(2119);
				match(BAD_ID);

				        notifyErrorListeners("Identifiers must start with a letter: " + _input.getText(_localctx.start, _input.LT(-1)));
				     
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 27:
			return or_expr_sempred((Or_exprContext)_localctx, predIndex);
		case 28:
			return and_expr_sempred((And_exprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean or_expr_sempred(Or_exprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean and_expr_sempred(And_exprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\u00c4\u084e\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4I"+
		"\tI\4J\tJ\4K\tK\4L\tL\4M\tM\4N\tN\4O\tO\4P\tP\4Q\tQ\4R\tR\4S\tS\4T\tT"+
		"\4U\tU\4V\tV\4W\tW\4X\tX\4Y\tY\4Z\tZ\4[\t[\4\\\t\\\4]\t]\4^\t^\4_\t_\4"+
		"`\t`\4a\ta\4b\tb\4c\tc\4d\td\4e\te\4f\tf\4g\tg\4h\th\4i\ti\4j\tj\4k\t"+
		"k\4l\tl\4m\tm\4n\tn\4o\to\4p\tp\4q\tq\4r\tr\4s\ts\4t\tt\4u\tu\4v\tv\4"+
		"w\tw\4x\tx\4y\ty\4z\tz\4{\t{\4|\t|\4}\t}\4~\t~\4\177\t\177\4\u0080\t\u0080"+
		"\4\u0081\t\u0081\4\u0082\t\u0082\4\u0083\t\u0083\4\u0084\t\u0084\4\u0085"+
		"\t\u0085\4\u0086\t\u0086\4\u0087\t\u0087\4\u0088\t\u0088\4\u0089\t\u0089"+
		"\4\u008a\t\u008a\4\u008b\t\u008b\4\u008c\t\u008c\4\u008d\t\u008d\4\u008e"+
		"\t\u008e\4\u008f\t\u008f\4\u0090\t\u0090\4\u0091\t\u0091\4\u0092\t\u0092"+
		"\4\u0093\t\u0093\4\u0094\t\u0094\4\u0095\t\u0095\4\u0096\t\u0096\4\u0097"+
		"\t\u0097\4\u0098\t\u0098\4\u0099\t\u0099\4\u009a\t\u009a\4\u009b\t\u009b"+
		"\4\u009c\t\u009c\4\u009d\t\u009d\4\u009e\t\u009e\4\u009f\t\u009f\4\u00a0"+
		"\t\u00a0\4\u00a1\t\u00a1\4\u00a2\t\u00a2\4\u00a3\t\u00a3\4\u00a4\t\u00a4"+
		"\4\u00a5\t\u00a5\4\u00a6\t\u00a6\4\u00a7\t\u00a7\4\u00a8\t\u00a8\4\u00a9"+
		"\t\u00a9\4\u00aa\t\u00aa\4\u00ab\t\u00ab\4\u00ac\t\u00ac\4\u00ad\t\u00ad"+
		"\4\u00ae\t\u00ae\4\u00af\t\u00af\4\u00b0\t\u00b0\4\u00b1\t\u00b1\4\u00b2"+
		"\t\u00b2\4\u00b3\t\u00b3\4\u00b4\t\u00b4\4\u00b5\t\u00b5\4\u00b6\t\u00b6"+
		"\4\u00b7\t\u00b7\4\u00b8\t\u00b8\4\u00b9\t\u00b9\4\u00ba\t\u00ba\4\u00bb"+
		"\t\u00bb\4\u00bc\t\u00bc\4\u00bd\t\u00bd\4\u00be\t\u00be\4\u00bf\t\u00bf"+
		"\4\u00c0\t\u00c0\4\u00c1\t\u00c1\4\u00c2\t\u00c2\4\u00c3\t\u00c3\4\u00c4"+
		"\t\u00c4\4\u00c5\t\u00c5\4\u00c6\t\u00c6\4\u00c7\t\u00c7\4\u00c8\t\u00c8"+
		"\4\u00c9\t\u00c9\3\2\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3\u01ae\n\3\3"+
		"\4\5\4\u01b1\n\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\7\5\u01bb\n\5\f\5\16"+
		"\5\u01be\13\5\3\6\3\6\3\6\3\7\3\7\3\b\3\b\3\b\5\b\u01c8\n\b\3\b\5\b\u01cb"+
		"\n\b\3\b\5\b\u01ce\n\b\3\b\5\b\u01d1\n\b\3\b\5\b\u01d4\n\b\3\t\3\t\3\t"+
		"\3\t\5\t\u01da\n\t\3\t\3\t\3\t\5\t\u01df\n\t\3\t\3\t\7\t\u01e3\n\t\f\t"+
		"\16\t\u01e6\13\t\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\5\n\u01f1\n\n\3\n"+
		"\3\n\3\n\3\n\3\n\5\n\u01f8\n\n\3\n\3\n\3\13\3\13\3\13\7\13\u01ff\n\13"+
		"\f\13\16\13\u0202\13\13\3\f\3\f\3\f\7\f\u0207\n\f\f\f\16\f\u020a\13\f"+
		"\3\r\3\r\3\r\7\r\u020f\n\r\f\r\16\r\u0212\13\r\3\16\3\16\3\16\3\17\3\17"+
		"\3\17\5\17\u021a\n\17\3\20\3\20\5\20\u021e\n\20\3\20\5\20\u0221\n\20\3"+
		"\21\3\21\5\21\u0225\n\21\3\22\3\22\3\22\3\23\3\23\3\23\3\24\5\24\u022e"+
		"\n\24\3\24\5\24\u0231\n\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\7\24\u023a"+
		"\n\24\f\24\16\24\u023d\13\24\5\24\u023f\n\24\3\25\3\25\7\25\u0243\n\25"+
		"\f\25\16\25\u0246\13\25\3\25\3\25\3\26\3\26\3\26\3\26\7\26\u024e\n\26"+
		"\f\26\16\26\u0251\13\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3"+
		"\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u0265\n\26\3\26\5\26"+
		"\u0268\n\26\3\27\3\27\5\27\u026c\n\27\3\30\3\30\3\30\3\30\3\30\3\30\3"+
		"\30\3\30\7\30\u0276\n\30\f\30\16\30\u0279\13\30\3\31\5\31\u027c\n\31\3"+
		"\31\3\31\5\31\u0280\n\31\3\32\3\32\3\32\3\32\3\32\7\32\u0287\n\32\f\32"+
		"\16\32\u028a\13\32\3\33\3\33\3\33\3\34\3\34\3\34\3\35\3\35\3\35\3\35\3"+
		"\35\3\35\7\35\u0298\n\35\f\35\16\35\u029b\13\35\3\36\3\36\3\36\3\36\3"+
		"\36\3\36\7\36\u02a3\n\36\f\36\16\36\u02a6\13\36\3\37\5\37\u02a9\n\37\3"+
		"\37\3\37\3 \3 \3 \5 \u02b0\n \3 \5 \u02b3\n \3!\3!\3!\3!\5!\u02b9\n!\3"+
		"\"\3\"\3\"\5\"\u02be\n\"\3\"\3\"\5\"\u02c2\n\"\3#\3#\3$\3$\3$\3$\3$\3"+
		"$\5$\u02cc\n$\3%\3%\3%\5%\u02d1\n%\3&\3&\3&\3&\3&\3&\6&\u02d9\n&\r&\16"+
		"&\u02da\3&\3&\3\'\3\'\3\'\3\'\7\'\u02e3\n\'\f\'\16\'\u02e6\13\'\3\'\3"+
		"\'\3(\3(\3(\3(\7(\u02ee\n(\f(\16(\u02f1\13(\3(\3(\3)\3)\3)\3)\3)\3)\6"+
		")\u02fb\n)\r)\16)\u02fc\3)\3)\3*\3*\3*\3*\3*\7*\u0306\n*\f*\16*\u0309"+
		"\13*\3*\3*\5*\u030d\n*\3*\3*\3*\3+\3+\3+\3,\3,\3,\5,\u0318\n,\3,\3,\5"+
		",\u031c\n,\3,\3,\5,\u0320\n,\3,\3,\3,\5,\u0325\n,\3,\7,\u0328\n,\f,\16"+
		",\u032b\13,\3,\3,\3-\3-\3-\7-\u0332\n-\f-\16-\u0335\13-\3.\3.\3.\7.\u033a"+
		"\n.\f.\16.\u033d\13.\3/\3/\3/\7/\u0342\n/\f/\16/\u0345\13/\3\60\3\60\3"+
		"\60\5\60\u034a\n\60\3\61\3\61\3\61\7\61\u034f\n\61\f\61\16\61\u0352\13"+
		"\61\3\62\3\62\3\62\5\62\u0357\n\62\3\63\3\63\3\63\3\63\3\63\5\63\u035e"+
		"\n\63\3\64\3\64\3\64\5\64\u0363\n\64\3\64\3\64\3\65\3\65\5\65\u0369\n"+
		"\65\3\66\3\66\5\66\u036d\n\66\3\66\3\66\5\66\u0371\n\66\3\66\3\66\3\67"+
		"\3\67\5\67\u0377\n\67\3\67\3\67\38\38\38\38\38\38\38\38\38\38\38\38\5"+
		"8\u0387\n8\39\39\39\59\u038c\n9\3:\3:\3:\3:\3:\5:\u0393\n:\3;\3;\3<\3"+
		"<\5<\u0399\n<\3<\3<\7<\u039d\n<\f<\16<\u03a0\13<\3<\3<\3=\3=\3=\3=\3="+
		"\3=\3=\3=\3=\7=\u03ad\n=\f=\16=\u03b0\13=\3=\3=\3=\3=\5=\u03b6\n=\3>\3"+
		">\3>\3>\3>\3>\3>\3?\3?\3@\3@\3@\3@\3@\7@\u03c6\n@\f@\16@\u03c9\13@\5@"+
		"\u03cb\n@\3@\3@\3A\3A\3A\3A\3A\3B\3B\3B\3B\3B\3B\3B\3B\3B\3B\7B\u03de"+
		"\nB\fB\16B\u03e1\13B\3B\3B\5B\u03e5\nB\3B\3B\3C\3C\3C\3C\3C\3C\3C\3D\3"+
		"D\3D\3D\3E\3E\3E\3E\3E\3E\3E\3F\5F\u03fc\nF\3F\3F\3F\3F\5F\u0402\nF\3"+
		"F\5F\u0405\nF\3F\3F\3F\3F\7F\u040b\nF\fF\16F\u040e\13F\3F\3F\5F\u0412"+
		"\nF\3F\3F\3F\3F\3F\7F\u0419\nF\fF\16F\u041c\13F\3F\3F\3F\3F\5F\u0422\n"+
		"F\3F\5F\u0425\nF\3G\3G\3G\3H\3H\5H\u042c\nH\3I\3I\3I\3I\3I\3I\5I\u0434"+
		"\nI\3J\5J\u0437\nJ\3J\3J\3J\5J\u043c\nJ\3J\5J\u043f\nJ\3J\3J\3J\7J\u0444"+
		"\nJ\fJ\16J\u0447\13J\3J\3J\3J\5J\u044c\nJ\3K\3K\3K\3L\3L\3L\3L\3L\5L\u0456"+
		"\nL\7L\u0458\nL\fL\16L\u045b\13L\3L\3L\3L\3L\3L\5L\u0462\nL\7L\u0464\n"+
		"L\fL\16L\u0467\13L\3L\3L\3L\3L\3L\5L\u046e\nL\7L\u0470\nL\fL\16L\u0473"+
		"\13L\3L\3L\3L\3L\7L\u0479\nL\fL\16L\u047c\13L\3L\3L\3L\3L\3L\7L\u0483"+
		"\nL\fL\16L\u0486\13L\5L\u0488\nL\3M\3M\3M\3M\3N\3N\5N\u0490\nN\3N\3N\3"+
		"O\3O\3O\3P\3P\3Q\3Q\3Q\3Q\3Q\3Q\5Q\u049f\nQ\3R\3R\3S\3S\3T\5T\u04a6\n"+
		"T\3T\3T\3T\3T\5T\u04ac\nT\3T\5T\u04af\nT\3T\3T\5T\u04b3\nT\3T\5T\u04b6"+
		"\nT\3U\3U\3U\3V\3V\5V\u04bd\nV\3W\3W\3W\3W\3W\3W\3W\3W\3W\3W\3W\3W\3W"+
		"\3W\3W\5W\u04ce\nW\3X\3X\3X\3X\3X\7X\u04d5\nX\fX\16X\u04d8\13X\3X\3X\3"+
		"Y\3Y\3Y\5Y\u04df\nY\3Y\5Y\u04e2\nY\3Z\3Z\5Z\u04e6\nZ\3Z\3Z\5Z\u04ea\n"+
		"Z\5Z\u04ec\nZ\3[\3[\3[\3[\3[\3[\5[\u04f4\n[\3\\\3\\\3\\\3]\3]\3]\3]\3"+
		"]\3^\3^\3^\3^\3^\3_\3_\3`\3`\3a\3a\3b\3b\3c\3c\3c\3c\3c\3c\3c\3c\3c\3"+
		"c\5c\u0515\nc\3d\3d\3e\3e\3e\3e\5e\u051d\ne\3f\3f\3f\3f\5f\u0523\nf\3"+
		"g\3g\3h\3h\3i\3i\3j\3j\3k\3k\3k\7k\u0530\nk\fk\16k\u0533\13k\3l\3l\3l"+
		"\7l\u0538\nl\fl\16l\u053b\13l\3m\5m\u053e\nm\3m\3m\3n\3n\3n\7n\u0545\n"+
		"n\fn\16n\u0548\13n\3o\3o\5o\u054c\no\3p\3p\3p\3p\3p\5p\u0553\np\3p\3p"+
		"\3q\3q\3q\3q\5q\u055b\nq\3q\3q\5q\u055f\nq\3r\3r\3s\3s\3s\3s\3t\3t\3t"+
		"\3t\3u\3u\3u\3u\3u\3v\3v\3v\3v\3v\5v\u0575\nv\3v\3v\5v\u0579\nv\3v\3v"+
		"\3v\3v\3v\3w\3w\3w\5w\u0583\nw\3w\3w\3x\3x\3y\3y\5y\u058b\ny\3y\3y\3y"+
		"\5y\u0590\ny\7y\u0592\ny\fy\16y\u0595\13y\3z\3z\3z\3z\3z\5z\u059c\nz\3"+
		"z\5z\u059f\nz\3{\3{\3{\3{\3{\5{\u05a6\n{\5{\u05a8\n{\3{\5{\u05ab\n{\3"+
		"{\3{\3|\3|\3|\3|\3|\3|\3|\3|\3|\5|\u05b8\n|\3}\3}\3}\7}\u05bd\n}\f}\16"+
		"}\u05c0\13}\3~\3~\5~\u05c4\n~\3\177\3\177\3\177\3\177\3\u0080\5\u0080"+
		"\u05cb\n\u0080\3\u0080\5\u0080\u05ce\n\u0080\3\u0080\5\u0080\u05d1\n\u0080"+
		"\3\u0080\5\u0080\u05d4\n\u0080\5\u0080\u05d6\n\u0080\3\u0081\3\u0081\3"+
		"\u0081\3\u0081\3\u0082\3\u0082\3\u0083\3\u0083\3\u0083\3\u0083\3\u0084"+
		"\3\u0084\3\u0084\3\u0084\3\u0085\3\u0085\3\u0085\3\u0085\3\u0086\3\u0086"+
		"\3\u0086\3\u0086\3\u0086\3\u0086\5\u0086\u05f0\n\u0086\5\u0086\u05f2\n"+
		"\u0086\3\u0086\3\u0086\3\u0086\3\u0086\6\u0086\u05f8\n\u0086\r\u0086\16"+
		"\u0086\u05f9\3\u0086\3\u0086\5\u0086\u05fe\n\u0086\3\u0087\3\u0087\3\u0087"+
		"\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087"+
		"\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087\3\u0087"+
		"\5\u0087\u0615\n\u0087\3\u0088\3\u0088\3\u0088\3\u0088\3\u0088\5\u0088"+
		"\u061c\n\u0088\3\u0089\3\u0089\3\u0089\3\u0089\3\u0089\3\u008a\3\u008a"+
		"\3\u008a\3\u008a\5\u008a\u0627\n\u008a\3\u008b\3\u008b\3\u008b\3\u008b"+
		"\5\u008b\u062d\n\u008b\3\u008b\3\u008b\3\u008b\3\u008b\5\u008b\u0633\n"+
		"\u008b\7\u008b\u0635\n\u008b\f\u008b\16\u008b\u0638\13\u008b\3\u008b\3"+
		"\u008b\3\u008c\3\u008c\3\u008c\3\u008c\3\u008c\3\u008c\5\u008c\u0642\n"+
		"\u008c\3\u008c\5\u008c\u0645\n\u008c\3\u008d\3\u008d\3\u008d\3\u008e\3"+
		"\u008e\3\u008e\3\u008e\5\u008e\u064e\n\u008e\3\u008e\5\u008e\u0651\n\u008e"+
		"\3\u008e\3\u008e\3\u008e\3\u008e\5\u008e\u0657\n\u008e\3\u008f\3\u008f"+
		"\3\u008f\7\u008f\u065c\n\u008f\f\u008f\16\u008f\u065f\13\u008f\3\u0090"+
		"\3\u0090\3\u0090\7\u0090\u0664\n\u0090\f\u0090\16\u0090\u0667\13\u0090"+
		"\3\u0091\3\u0091\3\u0091\7\u0091\u066c\n\u0091\f\u0091\16\u0091\u066f"+
		"\13\u0091\3\u0091\3\u0091\3\u0091\5\u0091\u0674\n\u0091\3\u0092\3\u0092"+
		"\3\u0092\3\u0092\5\u0092\u067a\n\u0092\3\u0092\3\u0092\3\u0093\3\u0093"+
		"\3\u0093\3\u0093\3\u0093\5\u0093\u0683\n\u0093\3\u0093\3\u0093\3\u0093"+
		"\3\u0093\3\u0093\3\u0093\3\u0093\3\u0093\5\u0093\u068d\n\u0093\3\u0093"+
		"\5\u0093\u0690\n\u0093\3\u0093\3\u0093\3\u0093\3\u0093\5\u0093\u0696\n"+
		"\u0093\3\u0093\5\u0093\u0699\n\u0093\3\u0094\3\u0094\3\u0095\3\u0095\3"+
		"\u0095\7\u0095\u06a0\n\u0095\f\u0095\16\u0095\u06a3\13\u0095\3\u0096\3"+
		"\u0096\5\u0096\u06a7\n\u0096\3\u0096\3\u0096\3\u0096\5\u0096\u06ac\n\u0096"+
		"\5\u0096\u06ae\n\u0096\3\u0097\3\u0097\3\u0097\3\u0097\3\u0097\3\u0097"+
		"\3\u0097\3\u0097\3\u0097\3\u0097\3\u0097\3\u0097\3\u0097\3\u0097\3\u0097"+
		"\3\u0097\5\u0097\u06c0\n\u0097\3\u0098\3\u0098\3\u0098\3\u0098\3\u0098"+
		"\3\u0098\3\u0098\3\u0098\3\u0098\3\u0098\3\u0098\3\u0098\3\u0098\3\u0098"+
		"\3\u0098\5\u0098\u06d1\n\u0098\3\u0098\3\u0098\5\u0098\u06d5\n\u0098\3"+
		"\u0099\3\u0099\3\u0099\3\u0099\3\u0099\3\u0099\3\u0099\3\u0099\3\u0099"+
		"\3\u0099\5\u0099\u06e1\n\u0099\3\u0099\5\u0099\u06e4\n\u0099\3\u009a\3"+
		"\u009a\3\u009a\3\u009a\3\u009a\3\u009a\5\u009a\u06ec\n\u009a\3\u009a\3"+
		"\u009a\3\u009a\3\u009a\3\u009a\5\u009a\u06f3\n\u009a\3\u009a\5\u009a\u06f6"+
		"\n\u009a\3\u009a\5\u009a\u06f9\n\u009a\3\u009b\3\u009b\3\u009b\3\u009b"+
		"\3\u009b\3\u009b\3\u009b\3\u009b\5\u009b\u0703\n\u009b\3\u009c\3\u009c"+
		"\3\u009c\7\u009c\u0708\n\u009c\f\u009c\16\u009c\u070b\13\u009c\3\u009d"+
		"\3\u009d\5\u009d\u070f\n\u009d\3\u009e\3\u009e\7\u009e\u0713\n\u009e\f"+
		"\u009e\16\u009e\u0716\13\u009e\3\u009f\3\u009f\3\u009f\3\u009f\3\u009f"+
		"\3\u009f\5\u009f\u071e\n\u009f\3\u00a0\3\u00a0\3\u00a0\3\u00a0\5\u00a0"+
		"\u0724\n\u00a0\3\u00a0\3\u00a0\3\u00a0\3\u00a0\5\u00a0\u072a\n\u00a0\3"+
		"\u00a1\3\u00a1\3\u00a1\5\u00a1\u072f\n\u00a1\3\u00a1\3\u00a1\3\u00a1\3"+
		"\u00a1\3\u00a1\3\u00a1\3\u00a1\3\u00a1\3\u00a1\3\u00a1\5\u00a1\u073b\n"+
		"\u00a1\3\u00a1\3\u00a1\3\u00a1\3\u00a1\3\u00a1\5\u00a1\u0742\n\u00a1\3"+
		"\u00a2\3\u00a2\3\u00a2\7\u00a2\u0747\n\u00a2\f\u00a2\16\u00a2\u074a\13"+
		"\u00a2\3\u00a3\3\u00a3\3\u00a3\5\u00a3\u074f\n\u00a3\3\u00a3\3\u00a3\3"+
		"\u00a3\3\u00a3\3\u00a3\3\u00a3\3\u00a3\3\u00a3\3\u00a3\3\u00a3\3\u00a3"+
		"\3\u00a3\3\u00a3\3\u00a3\5\u00a3\u075f\n\u00a3\3\u00a4\3\u00a4\3\u00a4"+
		"\3\u00a4\5\u00a4\u0765\n\u00a4\3\u00a4\5\u00a4\u0768\n\u00a4\3\u00a5\3"+
		"\u00a5\3\u00a5\3\u00a5\3\u00a6\3\u00a6\3\u00a6\3\u00a6\5\u00a6\u0772\n"+
		"\u00a6\3\u00a6\5\u00a6\u0775\n\u00a6\3\u00a6\5\u00a6\u0778\n\u00a6\3\u00a6"+
		"\5\u00a6\u077b\n\u00a6\3\u00a6\5\u00a6\u077e\n\u00a6\3\u00a7\3\u00a7\3"+
		"\u00a7\3\u00a7\5\u00a7\u0784\n\u00a7\3\u00a8\3\u00a8\3\u00a8\3\u00a8\3"+
		"\u00a9\3\u00a9\3\u00a9\3\u00a9\5\u00a9\u078e\n\u00a9\3\u00aa\3\u00aa\3"+
		"\u00aa\3\u00aa\5\u00aa\u0794\n\u00aa\3\u00ab\3\u00ab\5\u00ab\u0798\n\u00ab"+
		"\3\u00ac\3\u00ac\3\u00ac\3\u00ad\3\u00ad\3\u00ad\5\u00ad\u07a0\n\u00ad"+
		"\3\u00ad\5\u00ad\u07a3\n\u00ad\3\u00ad\3\u00ad\3\u00ad\5\u00ad\u07a8\n"+
		"\u00ad\3\u00ae\3\u00ae\3\u00ae\3\u00af\3\u00af\3\u00af\3\u00af\3\u00b0"+
		"\3\u00b0\5\u00b0\u07b3\n\u00b0\3\u00b1\3\u00b1\3\u00b1\3\u00b2\3\u00b2"+
		"\3\u00b2\3\u00b2\3\u00b3\3\u00b3\3\u00b3\3\u00b3\3\u00b4\3\u00b4\3\u00b4"+
		"\3\u00b4\3\u00b4\5\u00b4\u07c5\n\u00b4\3\u00b4\3\u00b4\3\u00b4\3\u00b5"+
		"\3\u00b5\3\u00b5\3\u00b5\3\u00b6\3\u00b6\3\u00b6\3\u00b6\3\u00b7\3\u00b7"+
		"\3\u00b7\3\u00b7\3\u00b7\5\u00b7\u07d7\n\u00b7\3\u00b7\3\u00b7\3\u00b7"+
		"\3\u00b8\3\u00b8\3\u00b8\3\u00b8\5\u00b8\u07e0\n\u00b8\3\u00b9\3\u00b9"+
		"\3\u00b9\7\u00b9\u07e5\n\u00b9\f\u00b9\16\u00b9\u07e8\13\u00b9\3\u00ba"+
		"\3\u00ba\5\u00ba\u07ec\n\u00ba\3\u00bb\3\u00bb\5\u00bb\u07f0\n\u00bb\3"+
		"\u00bb\3\u00bb\3\u00bb\5\u00bb\u07f5\n\u00bb\7\u00bb\u07f7\n\u00bb\f\u00bb"+
		"\16\u00bb\u07fa\13\u00bb\3\u00bc\3\u00bc\3\u00bd\3\u00bd\5\u00bd\u0800"+
		"\n\u00bd\3\u00be\3\u00be\3\u00be\3\u00be\7\u00be\u0806\n\u00be\f\u00be"+
		"\16\u00be\u0809\13\u00be\3\u00be\3\u00be\3\u00be\3\u00be\5\u00be\u080f"+
		"\n\u00be\3\u00bf\3\u00bf\3\u00bf\3\u00bf\7\u00bf\u0815\n\u00bf\f\u00bf"+
		"\16\u00bf\u0818\13\u00bf\3\u00bf\3\u00bf\3\u00bf\3\u00bf\5\u00bf\u081e"+
		"\n\u00bf\3\u00c0\3\u00c0\3\u00c0\3\u00c0\3\u00c1\3\u00c1\3\u00c1\3\u00c1"+
		"\3\u00c1\3\u00c1\3\u00c1\5\u00c1\u082b\n\u00c1\3\u00c2\3\u00c2\3\u00c2"+
		"\3\u00c3\3\u00c3\3\u00c3\3\u00c4\3\u00c4\3\u00c5\5\u00c5\u0836\n\u00c5"+
		"\3\u00c5\3\u00c5\3\u00c6\5\u00c6\u083b\n\u00c6\3\u00c6\3\u00c6\3\u00c7"+
		"\3\u00c7\3\u00c8\3\u00c8\3\u00c8\7\u00c8\u0844\n\u00c8\f\u00c8\16\u00c8"+
		"\u0847\13\u00c8\3\u00c9\3\u00c9\3\u00c9\5\u00c9\u084c\n\u00c9\3\u00c9"+
		"\2\48:\u00ca\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64"+
		"\668:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtvxz|~\u0080\u0082\u0084\u0086\u0088"+
		"\u008a\u008c\u008e\u0090\u0092\u0094\u0096\u0098\u009a\u009c\u009e\u00a0"+
		"\u00a2\u00a4\u00a6\u00a8\u00aa\u00ac\u00ae\u00b0\u00b2\u00b4\u00b6\u00b8"+
		"\u00ba\u00bc\u00be\u00c0\u00c2\u00c4\u00c6\u00c8\u00ca\u00cc\u00ce\u00d0"+
		"\u00d2\u00d4\u00d6\u00d8\u00da\u00dc\u00de\u00e0\u00e2\u00e4\u00e6\u00e8"+
		"\u00ea\u00ec\u00ee\u00f0\u00f2\u00f4\u00f6\u00f8\u00fa\u00fc\u00fe\u0100"+
		"\u0102\u0104\u0106\u0108\u010a\u010c\u010e\u0110\u0112\u0114\u0116\u0118"+
		"\u011a\u011c\u011e\u0120\u0122\u0124\u0126\u0128\u012a\u012c\u012e\u0130"+
		"\u0132\u0134\u0136\u0138\u013a\u013c\u013e\u0140\u0142\u0144\u0146\u0148"+
		"\u014a\u014c\u014e\u0150\u0152\u0154\u0156\u0158\u015a\u015c\u015e\u0160"+
		"\u0162\u0164\u0166\u0168\u016a\u016c\u016e\u0170\u0172\u0174\u0176\u0178"+
		"\u017a\u017c\u017e\u0180\u0182\u0184\u0186\u0188\u018a\u018c\u018e\u0190"+
		"\2\24\4\2\17\17\35\35\4\2))AA\3\2\u00a4\u00a9\3\2\u00b0\u00b1\4\2\u00a0"+
		"\u00a0\u00b2\u00b3\4\2@@xx\4\2\5\5\u00a2\u00a3\4\299tt\4\2\31\31\61\61"+
		"\5\2\u00a0\u00a0\u00a3\u00a3\u00b0\u00b0\3\2\u008a\u008b\5\2\u0086\u0086"+
		"\u0088\u0088\u008d\u008d\4\2\35\35\37\37\4\2FFrr\6\2\31\31\61\61HHee\3"+
		"\2\u00b8\u00ba\3\2\u00bb\u00bc\17\2\6\22\24),<>ACFHHJSUVXY\\{\u0083\u0096"+
		"\u00b3\u00b3\u00be\u00be\2\u08db\2\u0192\3\2\2\2\4\u01ad\3\2\2\2\6\u01b0"+
		"\3\2\2\2\b\u01b4\3\2\2\2\n\u01bf\3\2\2\2\f\u01c2\3\2\2\2\16\u01c4\3\2"+
		"\2\2\20\u01d5\3\2\2\2\22\u01e7\3\2\2\2\24\u01fb\3\2\2\2\26\u0203\3\2\2"+
		"\2\30\u020b\3\2\2\2\32\u0213\3\2\2\2\34\u0216\3\2\2\2\36\u021b\3\2\2\2"+
		" \u0224\3\2\2\2\"\u0226\3\2\2\2$\u0229\3\2\2\2&\u022d\3\2\2\2(\u0240\3"+
		"\2\2\2*\u0264\3\2\2\2,\u026b\3\2\2\2.\u026d\3\2\2\2\60\u027b\3\2\2\2\62"+
		"\u0281\3\2\2\2\64\u028b\3\2\2\2\66\u028e\3\2\2\28\u0291\3\2\2\2:\u029c"+
		"\3\2\2\2<\u02a8\3\2\2\2>\u02ac\3\2\2\2@\u02b8\3\2\2\2B\u02ba\3\2\2\2D"+
		"\u02c3\3\2\2\2F\u02cb\3\2\2\2H\u02d0\3\2\2\2J\u02d2\3\2\2\2L\u02de\3\2"+
		"\2\2N\u02e9\3\2\2\2P\u02f4\3\2\2\2R\u030c\3\2\2\2T\u0311\3\2\2\2V\u0314"+
		"\3\2\2\2X\u032e\3\2\2\2Z\u0336\3\2\2\2\\\u033e\3\2\2\2^\u0349\3\2\2\2"+
		"`\u034b\3\2\2\2b\u0353\3\2\2\2d\u035d\3\2\2\2f\u035f\3\2\2\2h\u0368\3"+
		"\2\2\2j\u036a\3\2\2\2l\u0374\3\2\2\2n\u0386\3\2\2\2p\u0388\3\2\2\2r\u0392"+
		"\3\2\2\2t\u0394\3\2\2\2v\u0396\3\2\2\2x\u03b5\3\2\2\2z\u03b7\3\2\2\2|"+
		"\u03be\3\2\2\2~\u03c0\3\2\2\2\u0080\u03ce\3\2\2\2\u0082\u03d3\3\2\2\2"+
		"\u0084\u03e8\3\2\2\2\u0086\u03ef\3\2\2\2\u0088\u03f3\3\2\2\2\u008a\u03fb"+
		"\3\2\2\2\u008c\u0426\3\2\2\2\u008e\u042b\3\2\2\2\u0090\u0433\3\2\2\2\u0092"+
		"\u0436\3\2\2\2\u0094\u044d\3\2\2\2\u0096\u0487\3\2\2\2\u0098\u0489\3\2"+
		"\2\2\u009a\u048d\3\2\2\2\u009c\u0493\3\2\2\2\u009e\u0496\3\2\2\2\u00a0"+
		"\u049e\3\2\2\2\u00a2\u04a0\3\2\2\2\u00a4\u04a2\3\2\2\2\u00a6\u04a5\3\2"+
		"\2\2\u00a8\u04b7\3\2\2\2\u00aa\u04ba\3\2\2\2\u00ac\u04cd\3\2\2\2\u00ae"+
		"\u04cf\3\2\2\2\u00b0\u04db\3\2\2\2\u00b2\u04eb\3\2\2\2\u00b4\u04ed\3\2"+
		"\2\2\u00b6\u04f5\3\2\2\2\u00b8\u04f8\3\2\2\2\u00ba\u04fd\3\2\2\2\u00bc"+
		"\u0502\3\2\2\2\u00be\u0504\3\2\2\2\u00c0\u0506\3\2\2\2\u00c2\u0508\3\2"+
		"\2\2\u00c4\u0514\3\2\2\2\u00c6\u0516\3\2\2\2\u00c8\u0518\3\2\2\2\u00ca"+
		"\u051e\3\2\2\2\u00cc\u0524\3\2\2\2\u00ce\u0526\3\2\2\2\u00d0\u0528\3\2"+
		"\2\2\u00d2\u052a\3\2\2\2\u00d4\u052c\3\2\2\2\u00d6\u0534\3\2\2\2\u00d8"+
		"\u053d\3\2\2\2\u00da\u0541\3\2\2\2\u00dc\u054b\3\2\2\2\u00de\u054d\3\2"+
		"\2\2\u00e0\u0556\3\2\2\2\u00e2\u0560\3\2\2\2\u00e4\u0562\3\2\2\2\u00e6"+
		"\u0566\3\2\2\2\u00e8\u056a\3\2\2\2\u00ea\u056f\3\2\2\2\u00ec\u0582\3\2"+
		"\2\2\u00ee\u0586\3\2\2\2\u00f0\u058a\3\2\2\2\u00f2\u0596\3\2\2\2\u00f4"+
		"\u05a0\3\2\2\2\u00f6\u05b7\3\2\2\2\u00f8\u05b9\3\2\2\2\u00fa\u05c1\3\2"+
		"\2\2\u00fc\u05c5\3\2\2\2\u00fe\u05d5\3\2\2\2\u0100\u05d7\3\2\2\2\u0102"+
		"\u05db\3\2\2\2\u0104\u05dd\3\2\2\2\u0106\u05e1\3\2\2\2\u0108\u05e5\3\2"+
		"\2\2\u010a\u05e9\3\2\2\2\u010c\u0614\3\2\2\2\u010e\u0616\3\2\2\2\u0110"+
		"\u061d\3\2\2\2\u0112\u0626\3\2\2\2\u0114\u0628\3\2\2\2\u0116\u063b\3\2"+
		"\2\2\u0118\u0646\3\2\2\2\u011a\u0649\3\2\2\2\u011c\u0658\3\2\2\2\u011e"+
		"\u0660\3\2\2\2\u0120\u0673\3\2\2\2\u0122\u0675\3\2\2\2\u0124\u067d\3\2"+
		"\2\2\u0126\u069a\3\2\2\2\u0128\u069c\3\2\2\2\u012a\u06ad\3\2\2\2\u012c"+
		"\u06bf\3\2\2\2\u012e\u06d0\3\2\2\2\u0130\u06d6\3\2\2\2\u0132\u06e5\3\2"+
		"\2\2\u0134\u0702\3\2\2\2\u0136\u0704\3\2\2\2\u0138\u070c\3\2\2\2\u013a"+
		"\u0710\3\2\2\2\u013c\u071d\3\2\2\2\u013e\u071f\3\2\2\2\u0140\u072b\3\2"+
		"\2\2\u0142\u0743\3\2\2\2\u0144\u074b\3\2\2\2\u0146\u0760\3\2\2\2\u0148"+
		"\u0769\3\2\2\2\u014a\u076d\3\2\2\2\u014c\u077f\3\2\2\2\u014e\u0785\3\2"+
		"\2\2\u0150\u0789\3\2\2\2\u0152\u078f\3\2\2\2\u0154\u0797\3\2\2\2\u0156"+
		"\u0799\3\2\2\2\u0158\u07a7\3\2\2\2\u015a\u07a9\3\2\2\2\u015c\u07ac\3\2"+
		"\2\2\u015e\u07b0\3\2\2\2\u0160\u07b4\3\2\2\2\u0162\u07b7\3\2\2\2\u0164"+
		"\u07bb\3\2\2\2\u0166\u07bf\3\2\2\2\u0168\u07c9\3\2\2\2\u016a\u07cd\3\2"+
		"\2\2\u016c\u07d1\3\2\2\2\u016e\u07df\3\2\2\2\u0170\u07e1\3\2\2\2\u0172"+
		"\u07eb\3\2\2\2\u0174\u07ef\3\2\2\2\u0176\u07fb\3\2\2\2\u0178\u07ff\3\2"+
		"\2\2\u017a\u080e\3\2\2\2\u017c\u081d\3\2\2\2\u017e\u081f\3\2\2\2\u0180"+
		"\u082a\3\2\2\2\u0182\u082c\3\2\2\2\u0184\u082f\3\2\2\2\u0186\u0832\3\2"+
		"\2\2\u0188\u0835\3\2\2\2\u018a\u083a\3\2\2\2\u018c\u083e\3\2\2\2\u018e"+
		"\u0840\3\2\2\2\u0190\u084b\3\2\2\2\u0192\u0193\5\4\3\2\u0193\u0194\7\2"+
		"\2\3\u0194\3\3\2\2\2\u0195\u01ae\5\6\4\2\u0196\u01ae\5\u008aF\2\u0197"+
		"\u01ae\5\u0092J\2\u0198\u01ae\5\u00a6T\2\u0199\u01ae\5\u00eav\2\u019a"+
		"\u01ae\5\u0124\u0093\2\u019b\u01ae\5\u0146\u00a4\2\u019c\u01ae\5\u0148"+
		"\u00a5\2\u019d\u01ae\5\u00dep\2\u019e\u01ae\5\u00e4s\2\u019f\u01ae\5\u013e"+
		"\u00a0\2\u01a0\u01ae\5\u00e0q\2\u01a1\u01ae\5\u00e6t\2\u01a2\u01ae\5\u0132"+
		"\u009a\2\u01a3\u01ae\5\u014e\u00a8\2\u01a4\u01ae\5\u014c\u00a7\2\u01a5"+
		"\u01ae\5\u0110\u0089\2\u01a6\u01ae\5\u014a\u00a6\2\u01a7\u01ae\5\u0122"+
		"\u0092\2\u01a8\u01ae\5\u0150\u00a9\2\u01a9\u01ae\5\u0152\u00aa\2\u01aa"+
		"\u01ae\5\u0140\u00a1\2\u01ab\u01ae\5\u00e8u\2\u01ac\u01ae\5\u0144\u00a3"+
		"\2\u01ad\u0195\3\2\2\2\u01ad\u0196\3\2\2\2\u01ad\u0197\3\2\2\2\u01ad\u0198"+
		"\3\2\2\2\u01ad\u0199\3\2\2\2\u01ad\u019a\3\2\2\2\u01ad\u019b\3\2\2\2\u01ad"+
		"\u019c\3\2\2\2\u01ad\u019d\3\2\2\2\u01ad\u019e\3\2\2\2\u01ad\u019f\3\2"+
		"\2\2\u01ad\u01a0\3\2\2\2\u01ad\u01a1\3\2\2\2\u01ad\u01a2\3\2\2\2\u01ad"+
		"\u01a3\3\2\2\2\u01ad\u01a4\3\2\2\2\u01ad\u01a5\3\2\2\2\u01ad\u01a6\3\2"+
		"\2\2\u01ad\u01a7\3\2\2\2\u01ad\u01a8\3\2\2\2\u01ad\u01a9\3\2\2\2\u01ad"+
		"\u01aa\3\2\2\2\u01ad\u01ab\3\2\2\2\u01ad\u01ac\3\2\2\2\u01ae\5\3\2\2\2"+
		"\u01af\u01b1\5\b\5\2\u01b0\u01af\3\2\2\2\u01b0\u01b1\3\2\2\2\u01b1\u01b2"+
		"\3\2\2\2\u01b2\u01b3\5\16\b\2\u01b3\7\3\2\2\2\u01b4\u01b5\7\32\2\2\u01b5"+
		"\u01b6\5\n\6\2\u01b6\u01bc\7\u0097\2\2\u01b7\u01b8\5\n\6\2\u01b8\u01b9"+
		"\7\u0097\2\2\u01b9\u01bb\3\2\2\2\u01ba\u01b7\3\2\2\2\u01bb\u01be\3\2\2"+
		"\2\u01bc\u01ba\3\2\2\2\u01bc\u01bd\3\2\2\2\u01bd\t\3\2\2\2\u01be\u01bc"+
		"\3\2\2\2\u01bf\u01c0\7\5\2\2\u01c0\u01c1\5\u00acW\2\u01c1\13\3\2\2\2\u01c2"+
		"\u01c3\58\35\2\u01c3\r\3\2\2\2\u01c4\u01c5\5$\23\2\u01c5\u01c7\5\20\t"+
		"\2\u01c6\u01c8\5\"\22\2\u01c7\u01c6\3\2\2\2\u01c7\u01c8\3\2\2\2\u01c8"+
		"\u01ca\3\2\2\2\u01c9\u01cb\5\62\32\2\u01ca\u01c9\3\2\2\2\u01ca\u01cb\3"+
		"\2\2\2\u01cb\u01cd\3\2\2\2\u01cc\u01ce\5.\30\2\u01cd\u01cc\3\2\2\2\u01cd"+
		"\u01ce\3\2\2\2\u01ce\u01d0\3\2\2\2\u01cf\u01d1\5\64\33\2\u01d0\u01cf\3"+
		"\2\2\2\u01d0\u01d1\3\2\2\2\u01d1\u01d3\3\2\2\2\u01d2\u01d4\5\66\34\2\u01d3"+
		"\u01d2\3\2\2\2\u01d3\u01d4\3\2\2\2\u01d4\17\3\2\2\2\u01d5\u01d9\7,\2\2"+
		"\u01d6\u01da\5\34\17\2\u01d7\u01da\5\22\n\2\u01d8\u01da\5\30\r\2\u01d9"+
		"\u01d6\3\2\2\2\u01d9\u01d7\3\2\2\2\u01d9\u01d8\3\2\2\2\u01da\u01e4\3\2"+
		"\2\2\u01db\u01dc\7\u0098\2\2\u01dc\u01de\5\f\7\2\u01dd\u01df\7\16\2\2"+
		"\u01de\u01dd\3\2\2\2\u01de\u01df\3\2\2\2\u01df\u01e0\3\2\2\2\u01e0\u01e1"+
		"\7\5\2\2\u01e1\u01e3\3\2\2\2\u01e2\u01db\3\2\2\2\u01e3\u01e6\3\2\2\2\u01e4"+
		"\u01e2\3\2\2\2\u01e4\u01e5\3\2\2\2\u01e5\21\3\2\2\2\u01e6\u01e4\3\2\2"+
		"\2\u01e7\u01e8\7M\2\2\u01e8\u01e9\7m\2\2\u01e9\u01ea\7\u009a\2\2\u01ea"+
		"\u01f0\5\34\17\2\u01eb\u01ec\7\f\2\2\u01ec\u01ed\7\u009a\2\2\u01ed\u01ee"+
		"\5\24\13\2\u01ee\u01ef\7\u009b\2\2\u01ef\u01f1\3\2\2\2\u01f0\u01eb\3\2"+
		"\2\2\u01f0\u01f1\3\2\2\2\u01f1\u01f7\3\2\2\2\u01f2\u01f3\7\36\2\2\u01f3"+
		"\u01f4\7\u009a\2\2\u01f4\u01f5\5\26\f\2\u01f5\u01f6\7\u009b\2\2\u01f6"+
		"\u01f8\3\2\2\2\u01f7\u01f2\3\2\2\2\u01f7\u01f8\3\2\2\2\u01f8\u01f9\3\2"+
		"\2\2\u01f9\u01fa\7\u009b\2\2\u01fa\23\3\2\2\2\u01fb\u0200\5\34\17\2\u01fc"+
		"\u01fd\7\u0098\2\2\u01fd\u01ff\5\34\17\2\u01fe\u01fc\3\2\2\2\u01ff\u0202"+
		"\3\2\2\2\u0200\u01fe\3\2\2\2\u0200\u0201\3\2\2\2\u0201\25\3\2\2\2\u0202"+
		"\u0200\3\2\2\2\u0203\u0208\5\34\17\2\u0204\u0205\7\u0098\2\2\u0205\u0207"+
		"\5\34\17\2\u0206\u0204\3\2\2\2\u0207\u020a\3\2\2\2\u0208\u0206\3\2\2\2"+
		"\u0208\u0209\3\2\2\2\u0209\27\3\2\2\2\u020a\u0208\3\2\2\2\u020b\u020c"+
		"\5\34\17\2\u020c\u0210\5\32\16\2\u020d\u020f\5\32\16\2\u020e\u020d\3\2"+
		"\2\2\u020f\u0212\3\2\2\2\u0210\u020e\3\2\2\2\u0210\u0211\3\2\2\2\u0211"+
		"\31\3\2\2\2\u0212\u0210\3\2\2\2\u0213\u0214\7\u0082\2\2\u0214\u0215\5"+
		"\34\17\2\u0215\33\3\2\2\2\u0216\u0219\5\36\20\2\u0217\u0218\7S\2\2\u0218"+
		"\u021a\58\35\2\u0219\u0217\3\2\2\2\u0219\u021a\3\2\2\2\u021a\35\3\2\2"+
		"\2\u021b\u0220\5\u00ecw\2\u021c\u021e\7\16\2\2\u021d\u021c\3\2\2\2\u021d"+
		"\u021e\3\2\2\2\u021e\u021f\3\2\2\2\u021f\u0221\5 \21\2\u0220\u021d\3\2"+
		"\2\2\u0220\u0221\3\2\2\2\u0221\37\3\2\2\2\u0222\u0225\7\5\2\2\u0223\u0225"+
		"\5\u0190\u00c9\2\u0224\u0222\3\2\2\2\u0224\u0223\3\2\2\2\u0225!\3\2\2"+
		"\2\u0226\u0227\7z\2\2\u0227\u0228\5\f\7\2\u0228#\3\2\2\2\u0229\u022a\7"+
		"f\2\2\u022a\u022b\5&\24\2\u022b%\3\2\2\2\u022c\u022e\5(\25\2\u022d\u022c"+
		"\3\2\2\2\u022d\u022e\3\2\2\2\u022e\u0230\3\2\2\2\u022f\u0231\7 \2\2\u0230"+
		"\u022f\3\2\2\2\u0230\u0231\3\2\2\2\u0231\u023e\3\2\2\2\u0232\u023f\7\u00a0"+
		"\2\2\u0233\u0234\5\f\7\2\u0234\u023b\5,\27\2\u0235\u0236\7\u0098\2\2\u0236"+
		"\u0237\5\f\7\2\u0237\u0238\5,\27\2\u0238\u023a\3\2\2\2\u0239\u0235\3\2"+
		"\2\2\u023a\u023d\3\2\2\2\u023b\u0239\3\2\2\2\u023b\u023c\3\2\2\2\u023c"+
		"\u023f\3\2\2\2\u023d\u023b\3\2\2\2\u023e\u0232\3\2\2\2\u023e\u0233\3\2"+
		"\2\2\u023f\'\3\2\2\2\u0240\u0244\7\3\2\2\u0241\u0243\5*\26\2\u0242\u0241"+
		"\3\2\2\2\u0243\u0246\3\2\2\2\u0244\u0242\3\2\2\2\u0244\u0245\3\2\2\2\u0245"+
		"\u0247\3\2\2\2\u0246\u0244\3\2\2\2\u0247\u0248\7\4\2\2\u0248)\3\2\2\2"+
		"\u0249\u024a\7Z\2\2\u024a\u024b\7\u009a\2\2\u024b\u024f\5\u00ecw\2\u024c"+
		"\u024e\5\u0126\u0094\2\u024d\u024c\3\2\2\2\u024e\u0251\3\2\2\2\u024f\u024d"+
		"\3\2\2\2\u024f\u0250\3\2\2\2\u0250\u0252\3\2\2\2\u0251\u024f\3\2\2\2\u0252"+
		"\u0253\7\u009b\2\2\u0253\u0265\3\2\2\2\u0254\u0255\7*\2\2\u0255\u0256"+
		"\7\u009a\2\2\u0256\u0257\5\u00ecw\2\u0257\u0258\5\u0126\u0094\2\u0258"+
		"\u0259\7\u009b\2\2\u0259\u0265\3\2\2\2\u025a\u025b\7[\2\2\u025b\u025c"+
		"\7\u009a\2\2\u025c\u025d\5\u00ecw\2\u025d\u025e\7\u009b\2\2\u025e\u0265"+
		"\3\2\2\2\u025f\u0260\7+\2\2\u0260\u0261\7\u009a\2\2\u0261\u0262\5\u00ec"+
		"w\2\u0262\u0263\7\u009b\2\2\u0263\u0265\3\2\2\2\u0264\u0249\3\2\2\2\u0264"+
		"\u0254\3\2\2\2\u0264\u025a\3\2\2\2\u0264\u025f\3\2\2\2\u0265\u0267\3\2"+
		"\2\2\u0266\u0268\7\u00bc\2\2\u0267\u0266\3\2\2\2\u0267\u0268\3\2\2\2\u0268"+
		"+\3\2\2\2\u0269\u026a\7\16\2\2\u026a\u026c\5\u0190\u00c9\2\u026b\u0269"+
		"\3\2\2\2\u026b\u026c\3\2\2\2\u026c-\3\2\2\2\u026d\u026e\7V\2\2\u026e\u026f"+
		"\7\20\2\2\u026f\u0270\5\f\7\2\u0270\u0277\5\60\31\2\u0271\u0272\7\u0098"+
		"\2\2\u0272\u0273\5\f\7\2\u0273\u0274\5\60\31\2\u0274\u0276\3\2\2\2\u0275"+
		"\u0271\3\2\2\2\u0276\u0279\3\2\2\2\u0277\u0275\3\2\2\2\u0277\u0278\3\2"+
		"\2\2\u0278/\3\2\2\2\u0279\u0277\3\2\2\2\u027a\u027c\t\2\2\2\u027b\u027a"+
		"\3\2\2\2\u027b\u027c\3\2\2\2\u027c\u027f\3\2\2\2\u027d\u027e\7P\2\2\u027e"+
		"\u0280\t\3\2\2\u027f\u027d\3\2\2\2\u027f\u0280\3\2\2\2\u0280\61\3\2\2"+
		"\2\u0281\u0282\7\60\2\2\u0282\u0283\7\20\2\2\u0283\u0288\5\f\7\2\u0284"+
		"\u0285\7\u0098\2\2\u0285\u0287\5\f\7\2\u0286\u0284\3\2\2\2\u0287\u028a"+
		"\3\2\2\2\u0288\u0286\3\2\2\2\u0288\u0289\3\2\2\2\u0289\63\3\2\2\2\u028a"+
		"\u0288\3\2\2\2\u028b\u028c\7D\2\2\u028c\u028d\5Z.\2\u028d\65\3\2\2\2\u028e"+
		"\u028f\7Q\2\2\u028f\u0290\5Z.\2\u0290\67\3\2\2\2\u0291\u0292\b\35\1\2"+
		"\u0292\u0293\5:\36\2\u0293\u0299\3\2\2\2\u0294\u0295\f\3\2\2\u0295\u0296"+
		"\7U\2\2\u0296\u0298\5:\36\2\u0297\u0294\3\2\2\2\u0298\u029b\3\2\2\2\u0299"+
		"\u0297\3\2\2\2\u0299\u029a\3\2\2\2\u029a9\3\2\2\2\u029b\u0299\3\2\2\2"+
		"\u029c\u029d\b\36\1\2\u029d\u029e\5<\37\2\u029e\u02a4\3\2\2\2\u029f\u02a0"+
		"\f\3\2\2\u02a0\u02a1\7\r\2\2\u02a1\u02a3\5<\37\2\u02a2\u029f\3\2\2\2\u02a3"+
		"\u02a6\3\2\2\2\u02a4\u02a2\3\2\2\2\u02a4\u02a5\3\2\2\2\u02a5;\3\2\2\2"+
		"\u02a6\u02a4\3\2\2\2\u02a7\u02a9\7O\2\2\u02a8\u02a7\3\2\2\2\u02a8\u02a9"+
		"\3\2\2\2\u02a9\u02aa\3\2\2\2\u02aa\u02ab\5> \2\u02ab=\3\2\2\2\u02ac\u02b2"+
		"\5@!\2\u02ad\u02af\7;\2\2\u02ae\u02b0\7O\2\2\u02af\u02ae\3\2\2\2\u02af"+
		"\u02b0\3\2\2\2\u02b0\u02b1\3\2\2\2\u02b1\u02b3\7\u00b5\2\2\u02b2\u02ad"+
		"\3\2\2\2\u02b2\u02b3\3\2\2\2\u02b3?\3\2\2\2\u02b4\u02b9\5B\"\2\u02b5\u02b9"+
		"\5H%\2\u02b6\u02b9\5T+\2\u02b7\u02b9\5V,\2\u02b8\u02b4\3\2\2\2\u02b8\u02b5"+
		"\3\2\2\2\u02b8\u02b6\3\2\2\2\u02b8\u02b7\3\2\2\2\u02b9A\3\2\2\2\u02ba"+
		"\u02c1\5X-\2\u02bb\u02be\5D#\2\u02bc\u02be\5F$\2\u02bd\u02bb\3\2\2\2\u02bd"+
		"\u02bc\3\2\2\2\u02be\u02bf\3\2\2\2\u02bf\u02c0\5X-\2\u02c0\u02c2\3\2\2"+
		"\2\u02c1\u02bd\3\2\2\2\u02c1\u02c2\3\2\2\2\u02c2C\3\2\2\2\u02c3\u02c4"+
		"\t\4\2\2\u02c4E\3\2\2\2\u02c5\u02cc\7\u00ae\2\2\u02c6\u02cc\7\u00af\2"+
		"\2\u02c7\u02cc\7\u00ac\2\2\u02c8\u02cc\7\u00ad\2\2\u02c9\u02cc\7\u00aa"+
		"\2\2\u02ca\u02cc\7\u00ab\2\2\u02cb\u02c5\3\2\2\2\u02cb\u02c6\3\2\2\2\u02cb"+
		"\u02c7\3\2\2\2\u02cb\u02c8\3\2\2\2\u02cb\u02c9\3\2\2\2\u02cb\u02ca\3\2"+
		"\2\2\u02ccG\3\2\2\2\u02cd\u02d1\5J&\2\u02ce\u02d1\5P)\2\u02cf\u02d1\5"+
		"R*\2\u02d0\u02cd\3\2\2\2\u02d0\u02ce\3\2\2\2\u02d0\u02cf\3\2\2\2\u02d1"+
		"I\3\2\2\2\u02d2\u02d3\5L\'\2\u02d3\u02d4\7\65\2\2\u02d4\u02d5\7\u009a"+
		"\2\2\u02d5\u02d8\5N(\2\u02d6\u02d7\7\u0098\2\2\u02d7\u02d9\5N(\2\u02d8"+
		"\u02d6\3\2\2\2\u02d9\u02da\3\2\2\2\u02da\u02d8\3\2\2\2\u02da\u02db\3\2"+
		"\2\2\u02db\u02dc\3\2\2\2\u02dc\u02dd\7\u009b\2\2\u02ddK\3\2\2\2\u02de"+
		"\u02df\7\u009a\2\2\u02df\u02e4\5X-\2\u02e0\u02e1\7\u0098\2\2\u02e1\u02e3"+
		"\5X-\2\u02e2\u02e0\3\2\2\2\u02e3\u02e6\3\2\2\2\u02e4\u02e2\3\2\2\2\u02e4"+
		"\u02e5\3\2\2\2\u02e5\u02e7\3\2\2\2\u02e6\u02e4\3\2\2\2\u02e7\u02e8\7\u009b"+
		"\2\2\u02e8M\3\2\2\2\u02e9\u02ea\7\u009a\2\2\u02ea\u02ef\5\f\7\2\u02eb"+
		"\u02ec\7\u0098\2\2\u02ec\u02ee\5\f\7\2\u02ed\u02eb\3\2\2\2\u02ee\u02f1"+
		"\3\2\2\2\u02ef\u02ed\3\2\2\2\u02ef\u02f0\3\2\2\2\u02f0\u02f2\3\2\2\2\u02f1"+
		"\u02ef\3\2\2\2\u02f2\u02f3\7\u009b\2\2\u02f3O\3\2\2\2\u02f4\u02f5\5X-"+
		"\2\u02f5\u02f6\7\65\2\2\u02f6\u02f7\7\u009a\2\2\u02f7\u02fa\5\f\7\2\u02f8"+
		"\u02f9\7\u0098\2\2\u02f9\u02fb\5\f\7\2\u02fa\u02f8\3\2\2\2\u02fb\u02fc"+
		"\3\2\2\2\u02fc\u02fa\3\2\2\2\u02fc\u02fd\3\2\2\2\u02fd\u02fe\3\2\2\2\u02fe"+
		"\u02ff\7\u009b\2\2\u02ffQ\3\2\2\2\u0300\u030d\5X-\2\u0301\u0302\7\u009a"+
		"\2\2\u0302\u0307\5X-\2\u0303\u0304\7\u0098\2\2\u0304\u0306\5X-\2\u0305"+
		"\u0303\3\2\2\2\u0306\u0309\3\2\2\2\u0307\u0305\3\2\2\2\u0307\u0308\3\2"+
		"\2\2\u0308\u030a\3\2\2\2\u0309\u0307\3\2\2\2\u030a\u030b\7\u009b\2\2\u030b"+
		"\u030d\3\2\2\2\u030c\u0300\3\2\2\2\u030c\u0301\3\2\2\2\u030d\u030e\3\2"+
		"\2\2\u030e\u030f\7\65\2\2\u030f\u0310\5`\61\2\u0310S\3\2\2\2\u0311\u0312"+
		"\7\'\2\2\u0312\u0313\5X-\2\u0313U\3\2\2\2\u0314\u0315\5X-\2\u0315\u0317"+
		"\7;\2\2\u0316\u0318\7O\2\2\u0317\u0316\3\2\2\2\u0317\u0318\3\2\2\2\u0318"+
		"\u0319\3\2\2\2\u0319\u031b\7R\2\2\u031a\u031c\7q\2\2\u031b\u031a\3\2\2"+
		"\2\u031b\u031c\3\2\2\2\u031c\u031d\3\2\2\2\u031d\u031f\7\u009a\2\2\u031e"+
		"\u0320\7T\2\2\u031f\u031e\3\2\2\2\u031f\u0320\3\2\2\2\u0320\u0321\3\2"+
		"\2\2\u0321\u0329\5\u00aaV\2\u0322\u0324\7\u0098\2\2\u0323\u0325\7T\2\2"+
		"\u0324\u0323\3\2\2\2\u0324\u0325\3\2\2\2\u0325\u0326\3\2\2\2\u0326\u0328"+
		"\5\u00aaV\2\u0327\u0322\3\2\2\2\u0328\u032b\3\2\2\2\u0329\u0327\3\2\2"+
		"\2\u0329\u032a\3\2\2\2\u032a\u032c\3\2\2\2\u032b\u0329\3\2\2\2\u032c\u032d"+
		"\7\u009b\2\2\u032dW\3\2\2\2\u032e\u0333\5Z.\2\u032f\u0330\7\u00b4\2\2"+
		"\u0330\u0332\5Z.\2\u0331\u032f\3\2\2\2\u0332\u0335\3\2\2\2\u0333\u0331"+
		"\3\2\2\2\u0333\u0334\3\2\2\2\u0334Y\3\2\2\2\u0335\u0333\3\2\2\2\u0336"+
		"\u033b\5\\/\2\u0337\u0338\t\5\2\2\u0338\u033a\5\\/\2\u0339\u0337\3\2\2"+
		"\2\u033a\u033d\3\2\2\2\u033b\u0339\3\2\2\2\u033b\u033c\3\2\2\2\u033c["+
		"\3\2\2\2\u033d\u033b\3\2\2\2\u033e\u0343\5^\60\2\u033f\u0340\t\6\2\2\u0340"+
		"\u0342\5^\60\2\u0341\u033f\3\2\2\2\u0342\u0345\3\2\2\2\u0343\u0341\3\2"+
		"\2\2\u0343\u0344\3\2\2\2\u0344]\3\2\2\2\u0345\u0343\3\2\2\2\u0346\u034a"+
		"\5`\61\2\u0347\u0348\t\5\2\2\u0348\u034a\5^\60\2\u0349\u0346\3\2\2\2\u0349"+
		"\u0347\3\2\2\2\u034a_\3\2\2\2\u034b\u0350\5n8\2\u034c\u034f\5b\62\2\u034d"+
		"\u034f\5h\65\2\u034e\u034c\3\2\2\2\u034e\u034d\3\2\2\2\u034f\u0352\3\2"+
		"\2\2\u0350\u034e\3\2\2\2\u0350\u0351\3\2\2\2\u0351a\3\2\2\2\u0352\u0350"+
		"\3\2\2\2\u0353\u0356\7\u00a1\2\2\u0354\u0357\5f\64\2\u0355\u0357\5d\63"+
		"\2\u0356\u0354\3\2\2\2\u0356\u0355\3\2\2\2\u0357c\3\2\2\2\u0358\u035e"+
		"\5\u0190\u00c9\2\u0359\u035e\5\u018c\u00c7\2\u035a\u035e\5t;\2\u035b\u035e"+
		"\5\u0086D\2\u035c\u035e\5~@\2\u035d\u0358\3\2\2\2\u035d\u0359\3\2\2\2"+
		"\u035d\u035a\3\2\2\2\u035d\u035b\3\2\2\2\u035d\u035c\3\2\2\2\u035ee\3"+
		"\2\2\2\u035f\u0360\t\7\2\2\u0360\u0362\7\u009a\2\2\u0361\u0363\5\f\7\2"+
		"\u0362\u0361\3\2\2\2\u0362\u0363\3\2\2\2\u0363\u0364\3\2\2\2\u0364\u0365"+
		"\7\u009b\2\2\u0365g\3\2\2\2\u0366\u0369\5l\67\2\u0367\u0369\5j\66\2\u0368"+
		"\u0366\3\2\2\2\u0368\u0367\3\2\2\2\u0369i\3\2\2\2\u036a\u036c\7\u009c"+
		"\2\2\u036b\u036d\5\f\7\2\u036c\u036b\3\2\2\2\u036c\u036d\3\2\2\2\u036d"+
		"\u036e\3\2\2\2\u036e\u0370\7\u0099\2\2\u036f\u0371\5\f\7\2\u0370\u036f"+
		"\3\2\2\2\u0370\u0371\3\2\2\2\u0371\u0372\3\2\2\2\u0372\u0373\7\u009d\2"+
		"\2\u0373k\3\2\2\2\u0374\u0376\7\u009c\2\2\u0375\u0377\5\f\7\2\u0376\u0375"+
		"\3\2\2\2\u0376\u0377\3\2\2\2\u0377\u0378\3\2\2\2\u0378\u0379\7\u009d\2"+
		"\2\u0379m\3\2\2\2\u037a\u0387\5r:\2\u037b\u0387\5p9\2\u037c\u0387\5t;"+
		"\2\u037d\u0387\5v<\2\u037e\u0387\5x=\2\u037f\u0387\5z>\2\u0380\u0387\5"+
		"~@\2\u0381\u0387\5\u0080A\2\u0382\u0387\5\u0082B\2\u0383\u0387\5\u0084"+
		"C\2\u0384\u0387\5\u0086D\2\u0385\u0387\5\u0088E\2\u0386\u037a\3\2\2\2"+
		"\u0386\u037b\3\2\2\2\u0386\u037c\3\2\2\2\u0386\u037d\3\2\2\2\u0386\u037e"+
		"\3\2\2\2\u0386\u037f\3\2\2\2\u0386\u0380\3\2\2\2\u0386\u0381\3\2\2\2\u0386"+
		"\u0382\3\2\2\2\u0386\u0383\3\2\2\2\u0386\u0384\3\2\2\2\u0386\u0385\3\2"+
		"\2\2\u0387o\3\2\2\2\u0388\u038b\5\u0190\u00c9\2\u0389\u038a\7\u00a1\2"+
		"\2\u038a\u038c\5\u0190\u00c9\2\u038b\u0389\3\2\2\2\u038b\u038c\3\2\2\2"+
		"\u038cq\3\2\2\2\u038d\u0393\5\u0188\u00c5\2\u038e\u0393\5\u018c\u00c7"+
		"\2\u038f\u0393\7\u00b7\2\2\u0390\u0393\7\u00b6\2\2\u0391\u0393\7\u00b5"+
		"\2\2\u0392\u038d\3\2\2\2\u0392\u038e\3\2\2\2\u0392\u038f\3\2\2\2\u0392"+
		"\u0390\3\2\2\2\u0392\u0391\3\2\2\2\u0393s\3\2\2\2\u0394\u0395\t\b\2\2"+
		"\u0395u\3\2\2\2\u0396\u0398\7\u009c\2\2\u0397\u0399\5\f\7\2\u0398\u0397"+
		"\3\2\2\2\u0398\u0399\3\2\2\2\u0399\u039e\3\2\2\2\u039a\u039b\7\u0098\2"+
		"\2\u039b\u039d\5\f\7\2\u039c\u039a\3\2\2\2\u039d\u03a0\3\2\2\2\u039e\u039c"+
		"\3\2\2\2\u039e\u039f\3\2\2\2\u039f\u03a1\3\2\2\2\u03a0\u039e\3\2\2\2\u03a1"+
		"\u03a2\7\u009d\2\2\u03a2w\3\2\2\2\u03a3\u03a4\7\u009e\2\2\u03a4\u03a5"+
		"\5\f\7\2\u03a5\u03a6\7\u0099\2\2\u03a6\u03ae\5\f\7\2\u03a7\u03a8\7\u0098"+
		"\2\2\u03a8\u03a9\5\f\7\2\u03a9\u03aa\7\u0099\2\2\u03aa\u03ab\5\f\7\2\u03ab"+
		"\u03ad\3\2\2\2\u03ac\u03a7\3\2\2\2\u03ad\u03b0\3\2\2\2\u03ae\u03ac\3\2"+
		"\2\2\u03ae\u03af\3\2\2\2\u03af\u03b1\3\2\2\2\u03b0\u03ae\3\2\2\2\u03b1"+
		"\u03b2\7\u009f\2\2\u03b2\u03b6\3\2\2\2\u03b3\u03b4\7\u009e\2\2\u03b4\u03b6"+
		"\7\u009f\2\2\u03b5\u03a3\3\2\2\2\u03b5\u03b3\3\2\2\2\u03b6y\3\2\2\2\u03b7"+
		"\u03b8\7g\2\2\u03b8\u03b9\7\u009a\2\2\u03b9\u03ba\5|?\2\u03ba\u03bb\7"+
		"\u0098\2\2\u03bb\u03bc\5\f\7\2\u03bc\u03bd\7\u009b\2\2\u03bd{\3\2\2\2"+
		"\u03be\u03bf\5\f\7\2\u03bf}\3\2\2\2\u03c0\u03c1\5\u0190\u00c9\2\u03c1"+
		"\u03ca\7\u009a\2\2\u03c2\u03c7\5\f\7\2\u03c3\u03c4\7\u0098\2\2\u03c4\u03c6"+
		"\5\f\7\2\u03c5\u03c3\3\2\2\2\u03c6\u03c9\3\2\2\2\u03c7\u03c5\3\2\2\2\u03c7"+
		"\u03c8\3\2\2\2\u03c8\u03cb\3\2\2\2\u03c9\u03c7\3\2\2\2\u03ca\u03c2\3\2"+
		"\2\2\u03ca\u03cb\3\2\2\2\u03cb\u03cc\3\2\2\2\u03cc\u03cd\7\u009b\2\2\u03cd"+
		"\177\3\2\2\2\u03ce\u03cf\7\26\2\2\u03cf\u03d0\7\u009a\2\2\u03d0\u03d1"+
		"\7\u00a0\2\2\u03d1\u03d2\7\u009b\2\2\u03d2\u0081\3\2\2\2\u03d3\u03d4\7"+
		"\22\2\2\u03d4\u03d5\7y\2\2\u03d5\u03d6\5\f\7\2\u03d6\u03d7\7n\2\2\u03d7"+
		"\u03df\5\f\7\2\u03d8\u03d9\7y\2\2\u03d9\u03da\5\f\7\2\u03da\u03db\7n\2"+
		"\2\u03db\u03dc\5\f\7\2\u03dc\u03de\3\2\2\2\u03dd\u03d8\3\2\2\2\u03de\u03e1"+
		"\3\2\2\2\u03df\u03dd\3\2\2\2\u03df\u03e0\3\2\2\2\u03e0\u03e4\3\2\2\2\u03e1"+
		"\u03df\3\2\2\2\u03e2\u03e3\7#\2\2\u03e3\u03e5\5\f\7\2\u03e4\u03e2\3\2"+
		"\2\2\u03e4\u03e5\3\2\2\2\u03e5\u03e6\3\2\2\2\u03e6\u03e7\7$\2\2\u03e7"+
		"\u0083\3\2\2\2\u03e8\u03e9\7\24\2\2\u03e9\u03ea\7\u009a\2\2\u03ea\u03eb"+
		"\5\f\7\2\u03eb\u03ec\7\16\2\2\u03ec\u03ed\5\u00aaV\2\u03ed\u03ee\7\u009b"+
		"\2\2\u03ee\u0085\3\2\2\2\u03ef\u03f0\7\u009a\2\2\u03f0\u03f1\5\f\7\2\u03f1"+
		"\u03f2\7\u009b\2\2\u03f2\u0087\3\2\2\2\u03f3\u03f4\7(\2\2\u03f4\u03f5"+
		"\7\u009a\2\2\u03f5\u03f6\5\u0190\u00c9\2\u03f6\u03f7\7,\2\2\u03f7\u03f8"+
		"\5\f\7\2\u03f8\u03f9\7\u009b\2\2\u03f9\u0089\3\2\2\2\u03fa\u03fc\5\b\5"+
		"\2\u03fb\u03fa\3\2\2\2\u03fb\u03fc\3\2\2\2\u03fc\u03fd\3\2\2\2\u03fd\u03fe"+
		"\t\t\2\2\u03fe\u03ff\7:\2\2\u03ff\u0404\5\u00ecw\2\u0400\u0402\7\16\2"+
		"\2\u0401\u0400\3\2\2\2\u0401\u0402\3\2\2\2\u0402\u0403\3\2\2\2\u0403\u0405"+
		"\5 \21\2\u0404\u0401\3\2\2\2\u0404\u0405\3\2\2\2\u0405\u0411\3\2\2\2\u0406"+
		"\u0407\7\u009a\2\2\u0407\u040c\5\u0190\u00c9\2\u0408\u0409\7\u0098\2\2"+
		"\u0409\u040b\5\u0190\u00c9\2\u040a\u0408\3\2\2\2\u040b\u040e\3\2\2\2\u040c"+
		"\u040a\3\2\2\2\u040c\u040d\3\2\2\2\u040d\u040f\3\2\2\2\u040e\u040c\3\2"+
		"\2\2\u040f\u0410\7\u009b\2\2\u0410\u0412\3\2\2\2\u0411\u0406\3\2\2\2\u0411"+
		"\u0412\3\2\2\2\u0412\u0413\3\2\2\2\u0413\u0414\7x\2\2\u0414\u0415\7\u009a"+
		"\2\2\u0415\u041a\5\u008eH\2\u0416\u0417\7\u0098\2\2\u0417\u0419\5\u008e"+
		"H\2\u0418\u0416\3\2\2\2\u0419\u041c\3\2\2\2\u041a\u0418\3\2\2\2\u041a"+
		"\u041b\3\2\2\2\u041b\u041d\3\2\2\2\u041c\u041a\3\2\2\2\u041d\u0421\7\u009b"+
		"\2\2\u041e\u041f\7h\2\2\u041f\u0420\7p\2\2\u0420\u0422\5\u0090I\2\u0421"+
		"\u041e\3\2\2\2\u0421\u0422\3\2\2\2\u0422\u0424\3\2\2\2\u0423\u0425\5\u008c"+
		"G\2\u0424\u0423\3\2\2\2\u0424\u0425\3\2\2\2\u0425\u008b\3\2\2\2\u0426"+
		"\u0427\7a\2\2\u0427\u0428\5&\24\2\u0428\u008d\3\2\2\2\u0429\u042c\7\33"+
		"\2\2\u042a\u042c\5\f\7\2\u042b\u0429\3\2\2\2\u042b\u042a\3\2\2\2\u042c"+
		"\u008f\3\2\2\2\u042d\u042e\5Z.\2\u042e\u042f\t\n\2\2\u042f\u0434\3\2\2"+
		"\2\u0430\u0431\7w\2\2\u0431\u0432\7l\2\2\u0432\u0434\7\33\2\2\u0433\u042d"+
		"\3\2\2\2\u0433\u0430\3\2\2\2\u0434\u0091\3\2\2\2\u0435\u0437\5\b\5\2\u0436"+
		"\u0435\3\2\2\2\u0436\u0437\3\2\2\2\u0437\u0438\3\2\2\2\u0438\u0439\7s"+
		"\2\2\u0439\u043e\5\u00ecw\2\u043a\u043c\7\16\2\2\u043b\u043a\3\2\2\2\u043b"+
		"\u043c\3\2\2\2\u043c\u043d\3\2\2\2\u043d\u043f\5 \21\2\u043e\u043b\3\2"+
		"\2\2\u043e\u043f\3\2\2\2\u043f\u0440\3\2\2\2\u0440\u0445\5\u0096L\2\u0441"+
		"\u0442\7\u0098\2\2\u0442\u0444\5\u0096L\2\u0443\u0441\3\2\2\2\u0444\u0447"+
		"\3\2\2\2\u0445\u0443\3\2\2\2\u0445\u0446\3\2\2\2\u0446\u0448\3\2\2\2\u0447"+
		"\u0445\3\2\2\2\u0448\u0449\7z\2\2\u0449\u044b\5\f\7\2\u044a\u044c\5\u0094"+
		"K\2\u044b\u044a\3\2\2\2\u044b\u044c\3\2\2\2\u044c\u0093\3\2\2\2\u044d"+
		"\u044e\7a\2\2\u044e\u044f\5&\24\2\u044f\u0095\3\2\2\2\u0450\u0451\7h\2"+
		"\2\u0451\u0459\5\u0098M\2\u0452\u0455\7\u0098\2\2\u0453\u0456\5\u0096"+
		"L\2\u0454\u0456\5\u0098M\2\u0455\u0453\3\2\2\2\u0455\u0454\3\2\2\2\u0456"+
		"\u0458\3\2\2\2\u0457\u0452\3\2\2\2\u0458\u045b\3\2\2\2\u0459\u0457\3\2"+
		"\2\2\u0459\u045a\3\2\2\2\u045a\u0488\3\2\2\2\u045b\u0459\3\2\2\2\u045c"+
		"\u045d\7\7\2\2\u045d\u0465\5\u009aN\2\u045e\u0461\7\u0098\2\2\u045f\u0462"+
		"\5\u0096L\2\u0460\u0462\5\u009aN\2\u0461\u045f\3\2\2\2\u0461\u0460\3\2"+
		"\2\2\u0462\u0464\3\2\2\2\u0463\u045e\3\2\2\2\u0464\u0467\3\2\2\2\u0465"+
		"\u0463\3\2\2\2\u0465\u0466\3\2\2\2\u0466\u0488\3\2\2\2\u0467\u0465\3\2"+
		"\2\2\u0468\u0469\7]\2\2\u0469\u0471\5\u009cO\2\u046a\u046d\7\u0098\2\2"+
		"\u046b\u046e\5\u0096L\2\u046c\u046e\5\u009cO\2\u046d\u046b\3\2\2\2\u046d"+
		"\u046c\3\2\2\2\u046e\u0470\3\2\2\2\u046f\u046a\3\2\2\2\u0470\u0473\3\2"+
		"\2\2\u0471\u046f\3\2\2\2\u0471\u0472\3\2\2\2\u0472\u0488\3\2\2\2\u0473"+
		"\u0471\3\2\2\2\u0474\u0475\7`\2\2\u0475\u047a\5\u009eP\2\u0476\u0477\7"+
		"\u0098\2\2\u0477\u0479\5\u009eP\2\u0478\u0476\3\2\2\2\u0479\u047c\3\2"+
		"\2\2\u047a\u0478\3\2\2\2\u047a\u047b\3\2\2\2\u047b\u0488\3\2\2\2\u047c"+
		"\u047a\3\2\2\2\u047d\u047e\7h\2\2\u047e\u047f\7p\2\2\u047f\u0484\5\u00a0"+
		"Q\2\u0480\u0481\7\u0098\2\2\u0481\u0483\5\u0096L\2\u0482\u0480\3\2\2\2"+
		"\u0483\u0486\3\2\2\2\u0484\u0482\3\2\2\2\u0484\u0485\3\2\2\2\u0485\u0488"+
		"\3\2\2\2\u0486\u0484\3\2\2\2\u0487\u0450\3\2\2\2\u0487\u045c\3\2\2\2\u0487"+
		"\u0468\3\2\2\2\u0487\u0474\3\2\2\2\u0487\u047d\3\2\2\2\u0488\u0097\3\2"+
		"\2\2\u0489\u048a\5\u00a2R\2\u048a\u048b\7\u00a8\2\2\u048b\u048c\5\f\7"+
		"\2\u048c\u0099\3\2\2\2\u048d\u048f\5\u00a2R\2\u048e\u0490\5\u00a4S\2\u048f"+
		"\u048e\3\2\2\2\u048f\u0490\3\2\2\2\u0490\u0491\3\2\2\2\u0491\u0492\5\f"+
		"\7\2\u0492\u009b\3\2\2\2\u0493\u0494\5\u00a2R\2\u0494\u0495\5\f\7\2\u0495"+
		"\u009d\3\2\2\2\u0496\u0497\5\u00a2R\2\u0497\u009f\3\2\2\2\u0498\u0499"+
		"\5Z.\2\u0499\u049a\t\n\2\2\u049a\u049f\3\2\2\2\u049b\u049c\7w\2\2\u049c"+
		"\u049d\7l\2\2\u049d\u049f\7\33\2\2\u049e\u0498\3\2\2\2\u049e\u049b\3\2"+
		"\2\2\u049f\u00a1\3\2\2\2\u04a0\u04a1\5`\61\2\u04a1\u00a3\3\2\2\2\u04a2"+
		"\u04a3\5Z.\2\u04a3\u00a5\3\2\2\2\u04a4\u04a6\5\b\5\2\u04a5\u04a4\3\2\2"+
		"\2\u04a5\u04a6\3\2\2\2\u04a6\u04a7\3\2\2\2\u04a7\u04a8\7\34\2\2\u04a8"+
		"\u04a9\7,\2\2\u04a9\u04ae\5\u00ecw\2\u04aa\u04ac\7\16\2\2\u04ab\u04aa"+
		"\3\2\2\2\u04ab\u04ac\3\2\2\2\u04ac\u04ad\3\2\2\2\u04ad\u04af\5 \21\2\u04ae"+
		"\u04ab\3\2\2\2\u04ae\u04af\3\2\2\2\u04af\u04b2\3\2\2\2\u04b0\u04b1\7z"+
		"\2\2\u04b1\u04b3\5\f\7\2\u04b2\u04b0\3\2\2\2\u04b2\u04b3\3\2\2\2\u04b3"+
		"\u04b5\3\2\2\2\u04b4\u04b6\5\u00a8U\2\u04b5\u04b4\3\2\2\2\u04b5\u04b6"+
		"\3\2\2\2\u04b6\u00a7\3\2\2\2\u04b7\u04b8\7a\2\2\u04b8\u04b9\5&\24\2\u04b9"+
		"\u00a9\3\2\2\2\u04ba\u04bc\5\u00acW\2\u04bb\u04bd\t\13\2\2\u04bc\u04bb"+
		"\3\2\2\2\u04bc\u04bd\3\2\2\2\u04bd\u00ab\3\2\2\2\u04be\u04ce\5\u00c8e"+
		"\2\u04bf\u04ce\5\u00ba^\2\u04c0\u04ce\5\u00c6d\2\u04c1\u04ce\5\u00c4c"+
		"\2\u04c2\u04ce\5\u00c0a\2\u04c3\u04ce\5\u00bc_\2\u04c4\u04ce\5\u00be`"+
		"\2\u04c5\u04ce\5\u00b8]\2\u04c6\u04ce\5\u00aeX\2\u04c7\u04ce\5\u00c2b"+
		"\2\u04c8\u04ce\5\u00caf\2\u04c9\u04ce\5\u00ccg\2\u04ca\u04ce\5\u00ceh"+
		"\2\u04cb\u04ce\5\u00d0i\2\u04cc\u04ce\5\u00d2j\2\u04cd\u04be\3\2\2\2\u04cd"+
		"\u04bf\3\2\2\2\u04cd\u04c0\3\2\2\2\u04cd\u04c1\3\2\2\2\u04cd\u04c2\3\2"+
		"\2\2\u04cd\u04c3\3\2\2\2\u04cd\u04c4\3\2\2\2\u04cd\u04c5\3\2\2\2\u04cd"+
		"\u04c6\3\2\2\2\u04cd\u04c7\3\2\2\2\u04cd\u04c8\3\2\2\2\u04cd\u04c9\3\2"+
		"\2\2\u04cd\u04ca\3\2\2\2\u04cd\u04cb\3\2\2\2\u04cd\u04cc\3\2\2\2\u04ce"+
		"\u00ad\3\2\2\2\u04cf\u04d0\7\u008f\2\2\u04d0\u04d1\7\u009a\2\2\u04d1\u04d6"+
		"\5\u00b0Y\2\u04d2\u04d3\7\u0098\2\2\u04d3\u04d5\5\u00b0Y\2\u04d4\u04d2"+
		"\3\2\2\2\u04d5\u04d8\3\2\2\2\u04d6\u04d4\3\2\2\2\u04d6\u04d7\3\2\2\2\u04d7"+
		"\u04d9\3\2\2\2\u04d8\u04d6\3\2\2\2\u04d9\u04da\7\u009b\2\2\u04da\u00af"+
		"\3\2\2\2\u04db\u04dc\5\u0190\u00c9\2\u04dc\u04de\5\u00acW\2\u04dd\u04df"+
		"\5\u00b2Z\2\u04de\u04dd\3\2\2\2\u04de\u04df\3\2\2\2\u04df\u04e1\3\2\2"+
		"\2\u04e0\u04e2\5\u0182\u00c2\2\u04e1\u04e0\3\2\2\2\u04e1\u04e2\3\2\2\2"+
		"\u04e2\u00b1\3\2\2\2\u04e3\u04e5\5\u00b4[\2\u04e4\u04e6\5\u00b6\\\2\u04e5"+
		"\u04e4\3\2\2\2\u04e5\u04e6\3\2\2\2\u04e6\u04ec\3\2\2\2\u04e7\u04e9\5\u00b6"+
		"\\\2\u04e8\u04ea\5\u00b4[\2\u04e9\u04e8\3\2\2\2\u04e9\u04ea\3\2\2\2\u04ea"+
		"\u04ec\3\2\2\2\u04eb\u04e3\3\2\2\2\u04eb\u04e7\3\2\2\2\u04ec\u00b3\3\2"+
		"\2\2\u04ed\u04f3\7\33\2\2\u04ee\u04f4\5\u0188\u00c5\2\u04ef\u04f4\5\u018c"+
		"\u00c7\2\u04f0\u04f4\7\u00b7\2\2\u04f1\u04f4\7\u00b6\2\2\u04f2\u04f4\5"+
		"\u0190\u00c9\2\u04f3\u04ee\3\2\2\2\u04f3\u04ef\3\2\2\2\u04f3\u04f0\3\2"+
		"\2\2\u04f3\u04f1\3\2\2\2\u04f3\u04f2\3\2\2\2\u04f4\u00b5\3\2\2\2\u04f5"+
		"\u04f6\7O\2\2\u04f6\u04f7\7\u00b5\2\2\u04f7\u00b7\3\2\2\2\u04f8\u04f9"+
		"\7\u008c\2\2\u04f9\u04fa\7\u009a\2\2\u04fa\u04fb\5\u00acW\2\u04fb\u04fc"+
		"\7\u009b\2\2\u04fc\u00b9\3\2\2\2\u04fd\u04fe\7\u0083\2\2\u04fe\u04ff\7"+
		"\u009a\2\2\u04ff\u0500\5\u00acW\2\u0500\u0501\7\u009b\2\2\u0501\u00bb"+
		"\3\2\2\2\u0502\u0503\t\f\2\2\u0503\u00bd\3\2\2\2\u0504\u0505\7<\2\2\u0505"+
		"\u00bf\3\2\2\2\u0506\u0507\t\r\2\2\u0507\u00c1\3\2\2\2\u0508\u0509\7\u0090"+
		"\2\2\u0509\u00c3\3\2\2\2\u050a\u050b\7\u0087\2\2\u050b\u050c\7\u009a\2"+
		"\2\u050c\u050d\5\u018e\u00c8\2\u050d\u050e\7\u009b\2\2\u050e\u0515\3\2"+
		"\2\2\u050f\u0510\7\u0087\2\2\u0510\u0511\7\u009a\2\2\u0511\u0512\5\u018e"+
		"\u00c8\2\u0512\u0513\bc\1\2\u0513\u0515\3\2\2\2\u0514\u050a\3\2\2\2\u0514"+
		"\u050f\3\2\2\2\u0515\u00c5\3\2\2\2\u0516\u0517\7\u0085\2\2\u0517\u00c7"+
		"\3\2\2\2\u0518\u051c\7\u0084\2\2\u0519\u051a\7\u009a\2\2\u051a\u051b\7"+
		"\u00b8\2\2\u051b\u051d\7\u009b\2\2\u051c\u0519\3\2\2\2\u051c\u051d\3\2"+
		"\2\2\u051d\u00c9\3\2\2\2\u051e\u0522\7\u0091\2\2\u051f\u0520\7\u009a\2"+
		"\2\u0520\u0521\7\u00b8\2\2\u0521\u0523\7\u009b\2\2\u0522\u051f\3\2\2\2"+
		"\u0522\u0523\3\2\2\2\u0523\u00cb\3\2\2\2\u0524\u0525\7\u0092\2\2\u0525"+
		"\u00cd\3\2\2\2\u0526\u0527\7\u0093\2\2\u0527\u00cf\3\2\2\2\u0528\u0529"+
		"\7\u0094\2\2\u0529\u00d1\3\2\2\2\u052a\u052b\7\u0095\2\2\u052b\u00d3\3"+
		"\2\2\2\u052c\u0531\5\u0190\u00c9\2\u052d\u052e\7\u00a1\2\2\u052e\u0530"+
		"\5\u0190\u00c9\2\u052f\u052d\3\2\2\2\u0530\u0533\3\2\2\2\u0531\u052f\3"+
		"\2\2\2\u0531\u0532\3\2\2\2\u0532\u00d5\3\2\2\2\u0533\u0531\3\2\2\2\u0534"+
		"\u0539\5\u00d8m\2\u0535\u0536\7\u00a1\2\2\u0536\u0538\5\u00d8m\2\u0537"+
		"\u0535\3\2\2\2\u0538\u053b\3\2\2\2\u0539\u0537\3\2\2\2\u0539\u053a\3\2"+
		"\2\2\u053a\u00d7\3\2\2\2\u053b\u0539\3\2\2\2\u053c\u053e\7\u00bd\2\2\u053d"+
		"\u053c\3\2\2\2\u053d\u053e\3\2\2\2\u053e\u053f\3\2\2\2\u053f\u0540\5\u0190"+
		"\u00c9\2\u0540\u00d9\3\2\2\2\u0541\u0546\5\u00dco\2\u0542\u0543\7\u00a1"+
		"\2\2\u0543\u0545\5\u00dco\2\u0544\u0542\3\2\2\2\u0545\u0548\3\2\2\2\u0546"+
		"\u0544\3\2\2\2\u0546\u0547\3\2\2\2\u0547\u00db\3\2\2\2\u0548\u0546\3\2"+
		"\2\2\u0549\u054c\5\u0190\u00c9\2\u054a\u054c\7\u00bb\2\2\u054b\u0549\3"+
		"\2\2\2\u054b\u054a\3\2\2\2\u054c\u00dd\3\2\2\2\u054d\u054e\7\27\2\2\u054e"+
		"\u0552\7K\2\2\u054f\u0550\7\64\2\2\u0550\u0551\7O\2\2\u0551\u0553\7\'"+
		"\2\2\u0552\u054f\3\2\2\2\u0552\u0553\3\2\2\2\u0553\u0554\3\2\2\2\u0554"+
		"\u0555\5\u00eex\2\u0555\u00df\3\2\2\2\u0556\u0557\7!\2\2\u0557\u055a\7"+
		"K\2\2\u0558\u0559\7\64\2\2\u0559\u055b\7\'\2\2\u055a\u0558\3\2\2\2\u055a"+
		"\u055b\3\2\2\2\u055b\u055c\3\2\2\2\u055c\u055e\5\u00eex\2\u055d\u055f"+
		"\7\23\2\2\u055e\u055d\3\2\2\2\u055e\u055f\3\2\2\2\u055f\u00e1\3\2\2\2"+
		"\u0560\u0561\5\u0190\u00c9\2\u0561\u00e3\3\2\2\2\u0562\u0563\7\27\2\2"+
		"\u0563\u0564\7^\2\2\u0564\u0565\5\u00e2r\2\u0565\u00e5\3\2\2\2\u0566\u0567"+
		"\7!\2\2\u0567\u0568\7^\2\2\u0568\u0569\5\u00e2r\2\u0569\u00e7\3\2\2\2"+
		"\u056a\u056b\7h\2\2\u056b\u056c\7E\2\2\u056c\u056d\7^\2\2\u056d\u056e"+
		"\5\u00e2r\2\u056e\u00e9\3\2\2\2\u056f\u0570\7\27\2\2\u0570\u0574\7l\2"+
		"\2\u0571\u0572\7\64\2\2\u0572\u0573\7O\2\2\u0573\u0575\7\'\2\2\u0574\u0571"+
		"\3\2\2\2\u0574\u0575\3\2\2\2\u0575\u0576\3\2\2\2\u0576\u0578\5\u00ecw"+
		"\2\u0577\u0579\5\u0182\u00c2\2\u0578\u0577\3\2\2\2\u0578\u0579\3\2\2\2"+
		"\u0579\u057a\3\2\2\2\u057a\u057b\7\u009a\2\2\u057b\u057c\5\u00f0y\2\u057c"+
		"\u057d\7\u009b\2\2\u057d\u057e\5\u00fe\u0080\2\u057e\u00eb\3\2\2\2\u057f"+
		"\u0580\5\u00eex\2\u0580\u0581\7\u0099\2\2\u0581\u0583\3\2\2\2\u0582\u057f"+
		"\3\2\2\2\u0582\u0583\3\2\2\2\u0583\u0584\3\2\2\2\u0584\u0585\5\u00d6l"+
		"\2\u0585\u00ed\3\2\2\2\u0586\u0587\5\u00d4k\2\u0587\u00ef\3\2\2\2\u0588"+
		"\u058b\5\u00f2z\2\u0589\u058b\5\u00f4{\2\u058a\u0588\3\2\2\2\u058a\u0589"+
		"\3\2\2\2\u058b\u0593\3\2\2\2\u058c\u058f\7\u0098\2\2\u058d\u0590\5\u00f2"+
		"z\2\u058e\u0590\5\u00f4{\2\u058f\u058d\3\2\2\2\u058f\u058e\3\2\2\2\u0590"+
		"\u0592\3\2\2\2\u0591\u058c\3\2\2\2\u0592\u0595\3\2\2\2\u0593\u0591\3\2"+
		"\2\2\u0593\u0594\3\2\2\2\u0594\u00f1\3\2\2\2\u0595\u0593\3\2\2\2\u0596"+
		"\u0597\5\u0190\u00c9\2\u0597\u059b\5\u00acW\2\u0598\u059c\5\u00b2Z\2\u0599"+
		"\u059c\5\u010a\u0086\2\u059a\u059c\5\u010e\u0088\2\u059b\u0598\3\2\2\2"+
		"\u059b\u0599\3\2\2\2\u059b\u059a\3\2\2\2\u059b\u059c\3\2\2\2\u059c\u059e"+
		"\3\2\2\2\u059d\u059f\5\u0182\u00c2\2\u059e\u059d\3\2\2\2\u059e\u059f\3"+
		"\2\2\2\u059f\u00f3\3\2\2\2\u05a0\u05a1\7\\\2\2\u05a1\u05a2\7>\2\2\u05a2"+
		"\u05a7\7\u009a\2\2\u05a3\u05a5\5\u00f6|\2\u05a4\u05a6\7\u0098\2\2\u05a5"+
		"\u05a4\3\2\2\2\u05a5\u05a6\3\2\2\2\u05a6\u05a8\3\2\2\2\u05a7\u05a3\3\2"+
		"\2\2\u05a7\u05a8\3\2\2\2\u05a8\u05aa\3\2\2\2\u05a9\u05ab\5\u00f8}\2\u05aa"+
		"\u05a9\3\2\2\2\u05aa\u05ab\3\2\2\2\u05ab\u05ac\3\2\2\2\u05ac\u05ad\7\u009b"+
		"\2\2\u05ad\u00f5\3\2\2\2\u05ae\u05af\7i\2\2\u05af\u05b0\7\u009a\2\2\u05b0"+
		"\u05b1\5\u00f8}\2\u05b1\u05b2\7\u009b\2\2\u05b2\u05b8\3\2\2\2\u05b3\u05b4"+
		"\7\u009a\2\2\u05b4\u05b5\5\u00f8}\2\u05b5\u05b6\b|\1\2\u05b6\u05b8\3\2"+
		"\2\2\u05b7\u05ae\3\2\2\2\u05b7\u05b3\3\2\2\2\u05b8\u00f7\3\2\2\2\u05b9"+
		"\u05be\5\u00fa~\2\u05ba\u05bb\7\u0098\2\2\u05bb\u05bd\5\u00fa~\2\u05bc"+
		"\u05ba\3\2\2\2\u05bd\u05c0\3\2\2\2\u05be\u05bc\3\2\2\2\u05be\u05bf\3\2"+
		"\2\2\u05bf\u00f9\3\2\2\2\u05c0\u05be\3\2\2\2\u05c1\u05c3\5\u0190\u00c9"+
		"\2\u05c2\u05c4\5\u00fc\177\2\u05c3\u05c2\3\2\2\2\u05c3\u05c4\3\2\2\2\u05c4"+
		"\u00fb\3\2\2\2\u05c5\u05c6\7\u009a\2\2\u05c6\u05c7\7\u00b8\2\2\u05c7\u05c8"+
		"\7\u009b\2\2\u05c8\u00fd\3\2\2\2\u05c9\u05cb\5\u0100\u0081\2\u05ca\u05c9"+
		"\3\2\2\2\u05ca\u05cb\3\2\2\2\u05cb\u05cd\3\2\2\2\u05cc\u05ce\5\u0104\u0083"+
		"\2\u05cd\u05cc\3\2\2\2\u05cd\u05ce\3\2\2\2\u05ce\u05d6\3\2\2\2\u05cf\u05d1"+
		"\5\u0104\u0083\2\u05d0\u05cf\3\2\2\2\u05d0\u05d1\3\2\2\2\u05d1\u05d3\3"+
		"\2\2\2\u05d2\u05d4\5\u0100\u0081\2\u05d3\u05d2\3\2\2\2\u05d3\u05d4\3\2"+
		"\2\2\u05d4\u05d6\3\2\2\2\u05d5\u05ca\3\2\2\2\u05d5\u05d0\3\2\2\2\u05d6"+
		"\u00ff\3\2\2\2\u05d7\u05d8\7w\2\2\u05d8\u05d9\7p\2\2\u05d9\u05da\5\u0184"+
		"\u00c3\2\u05da\u0101\3\2\2\2\u05db\u05dc\5\u018e\u00c8\2\u05dc\u0103\3"+
		"\2\2\2\u05dd\u05de\7\65\2\2\u05de\u05df\7_\2\2\u05df\u05e0\5\u0102\u0082"+
		"\2\u05e0\u0105\3\2\2\2\u05e1\u05e2\7\7\2\2\u05e2\u05e3\7_\2\2\u05e3\u05e4"+
		"\5\u0102\u0082\2\u05e4\u0107\3\2\2\2\u05e5\u05e6\7!\2\2\u05e6\u05e7\7"+
		"_\2\2\u05e7\u05e8\5\u0102\u0082\2\u05e8\u0109\3\2\2\2\u05e9\u05f1\7.\2"+
		"\2\u05ea\u05f2\7\13\2\2\u05eb\u05ec\7\20\2\2\u05ec\u05ef\7\33\2\2\u05ed"+
		"\u05ee\7S\2\2\u05ee\u05f0\7\u00b5\2\2\u05ef\u05ed\3\2\2\2\u05ef\u05f0"+
		"\3\2\2\2\u05f0\u05f2\3\2\2\2\u05f1\u05ea\3\2\2\2\u05f1\u05eb\3\2\2\2\u05f2"+
		"\u05f3\3\2\2\2\u05f3\u05f4\7\16\2\2\u05f4\u05fd\7\63\2\2\u05f5\u05f7\7"+
		"\u009a\2\2\u05f6\u05f8\5\u010c\u0087\2\u05f7\u05f6\3\2\2\2\u05f8\u05f9"+
		"\3\2\2\2\u05f9\u05f7\3\2\2\2\u05f9\u05fa\3\2\2\2\u05fa\u05fb\3\2\2\2\u05fb"+
		"\u05fc\7\u009b\2\2\u05fc\u05fe\3\2\2\2\u05fd\u05f5\3\2\2\2\u05fd\u05fe"+
		"\3\2\2\2\u05fe\u010b\3\2\2\2\u05ff\u0600\7k\2\2\u0600\u0601\7{\2\2\u0601"+
		"\u0615\5\u018a\u00c6\2\u0602\u0603\7\66\2\2\u0603\u0604\7\20\2\2\u0604"+
		"\u0615\5\u018a\u00c6\2\u0605\u0606\7G\2\2\u0606\u0615\5\u018a\u00c6\2"+
		"\u0607\u0608\7N\2\2\u0608\u0615\7G\2\2\u0609\u060a\7I\2\2\u060a\u0615"+
		"\5\u018a\u00c6\2\u060b\u060c\7N\2\2\u060c\u0615\7I\2\2\u060d\u060e\7\21"+
		"\2\2\u060e\u0615\7\u00b8\2\2\u060f\u0610\7N\2\2\u0610\u0615\7\21\2\2\u0611"+
		"\u0615\7\30\2\2\u0612\u0613\7N\2\2\u0613\u0615\7\30\2\2\u0614\u05ff\3"+
		"\2\2\2\u0614\u0602\3\2\2\2\u0614\u0605\3\2\2\2\u0614\u0607\3\2\2\2\u0614"+
		"\u0609\3\2\2\2\u0614\u060b\3\2\2\2\u0614\u060d\3\2\2\2\u0614\u060f\3\2"+
		"\2\2\u0614\u0611\3\2\2\2\u0614\u0612\3\2\2\2\u0615\u010d\3\2\2\2\u0616"+
		"\u0617\7\16\2\2\u0617\u061b\7|\2\2\u0618\u0619\7.\2\2\u0619\u061a\7\20"+
		"\2\2\u061a\u061c\7\33\2\2\u061b\u0618\3\2\2\2\u061b\u061c\3\2\2\2\u061c"+
		"\u010f\3\2\2\2\u061d\u061e\7\n\2\2\u061e\u061f\7l\2\2\u061f\u0620\5\u00ec"+
		"w\2\u0620\u0621\5\u0112\u008a\2\u0621\u0111\3\2\2\2\u0622\u0627\5\u0114"+
		"\u008b\2\u0623\u0627\5\u0100\u0081\2\u0624\u0627\5\u0106\u0084\2\u0625"+
		"\u0627\5\u0108\u0085\2\u0626\u0622\3\2\2\2\u0626\u0623\3\2\2\2\u0626\u0624"+
		"\3\2\2\2\u0626\u0625\3\2\2\2\u0627\u0113\3\2\2\2\u0628\u062c\7\u009a\2"+
		"\2\u0629\u062d\5\u0116\u008c\2\u062a\u062d\5\u0118\u008d\2\u062b\u062d"+
		"\5\u011a\u008e\2\u062c\u0629\3\2\2\2\u062c\u062a\3\2\2\2\u062c\u062b\3"+
		"\2\2\2\u062d\u0636\3\2\2\2\u062e\u0632\7\u0098\2\2\u062f\u0633\5\u0116"+
		"\u008c\2\u0630\u0633\5\u0118\u008d\2\u0631\u0633\5\u011a\u008e\2\u0632"+
		"\u062f\3\2\2\2\u0632\u0630\3\2\2\2\u0632\u0631\3\2\2\2\u0633\u0635\3\2"+
		"\2\2\u0634\u062e\3\2\2\2\u0635\u0638\3\2\2\2\u0636\u0634\3\2\2\2\u0636"+
		"\u0637\3\2\2\2\u0637\u0639\3\2\2\2\u0638\u0636\3\2\2\2\u0639\u063a\7\u009b"+
		"\2\2\u063a\u0115\3\2\2\2\u063b\u063c\7\7\2\2\u063c\u063d\5\u011c\u008f"+
		"\2\u063d\u0641\5\u00acW\2\u063e\u0642\5\u00b2Z\2\u063f\u0642\5\u010a\u0086"+
		"\2\u0640\u0642\5\u010e\u0088\2\u0641\u063e\3\2\2\2\u0641\u063f\3\2\2\2"+
		"\u0641\u0640\3\2\2\2\u0641\u0642\3\2\2\2\u0642\u0644\3\2\2\2\u0643\u0645"+
		"\5\u0182\u00c2\2\u0644\u0643\3\2\2\2\u0644\u0645\3\2\2\2\u0645\u0117\3"+
		"\2\2\2\u0646\u0647\7!\2\2\u0647\u0648\5\u011c\u008f\2\u0648\u0119\3\2"+
		"\2\2\u0649\u064a\7J\2\2\u064a\u0656\5\u011c\u008f\2\u064b\u064d\5\u00ac"+
		"W\2\u064c\u064e\5\u00b2Z\2\u064d\u064c\3\2\2\2\u064d\u064e\3\2\2\2\u064e"+
		"\u0650\3\2\2\2\u064f\u0651\5\u0182\u00c2\2\u0650\u064f\3\2\2\2\u0650\u0651"+
		"\3\2\2\2\u0651\u0657\3\2\2\2\u0652\u0657\5\u010a\u0086\2\u0653\u0657\5"+
		"\u010e\u0088\2\u0654\u0655\7!\2\2\u0655\u0657\7\63\2\2\u0656\u064b\3\2"+
		"\2\2\u0656\u0652\3\2\2\2\u0656\u0653\3\2\2\2\u0656\u0654\3\2\2\2\u0657"+
		"\u011b\3\2\2\2\u0658\u065d\5\u011e\u0090\2\u0659\u065a\7\u00a1\2\2\u065a"+
		"\u065c\5\u0120\u0091\2\u065b\u0659\3\2\2\2\u065c\u065f\3\2\2\2\u065d\u065b"+
		"\3\2\2\2\u065d\u065e\3\2\2\2\u065e\u011d\3\2\2\2\u065f\u065d\3\2\2\2\u0660"+
		"\u0665\5\u0190\u00c9\2\u0661\u0662\7\u009c\2\2\u0662\u0664\7\u009d\2\2"+
		"\u0663\u0661\3\2\2\2\u0664\u0667\3\2\2\2\u0665\u0663\3\2\2\2\u0665\u0666"+
		"\3\2\2\2\u0666\u011f\3\2\2\2\u0667\u0665\3\2\2\2\u0668\u066d\5\u0190\u00c9"+
		"\2\u0669\u066a\7\u009c\2\2\u066a\u066c\7\u009d\2\2\u066b\u0669\3\2\2\2"+
		"\u066c\u066f\3\2\2\2\u066d\u066b\3\2\2\2\u066d\u066e\3\2\2\2\u066e\u0674"+
		"\3\2\2\2\u066f\u066d\3\2\2\2\u0670\u0671\7x\2\2\u0671\u0672\7\u009a\2"+
		"\2\u0672\u0674\7\u009b\2\2\u0673\u0668\3\2\2\2\u0673\u0670\3\2\2\2\u0674"+
		"\u0121\3\2\2\2\u0675\u0676\7!\2\2\u0676\u0679\7l\2\2\u0677\u0678\7\64"+
		"\2\2\u0678\u067a\7\'\2\2\u0679\u0677\3\2\2\2\u0679\u067a\3\2\2\2\u067a"+
		"\u067b\3\2\2\2\u067b\u067c\5\u00ecw\2\u067c\u0123\3\2\2\2\u067d\u067e"+
		"\7\27\2\2\u067e\u0682\7\67\2\2\u067f\u0680\7\64\2\2\u0680\u0681\7O\2\2"+
		"\u0681\u0683\7\'\2\2\u0682\u067f\3\2\2\2\u0682\u0683\3\2\2\2\u0683\u0684"+
		"\3\2\2\2\u0684\u0685\5\u0126\u0094\2\u0685\u0686\7S\2\2\u0686\u0695\5"+
		"\u00ecw\2\u0687\u0688\7\u009a\2\2\u0688\u0689\5\u0128\u0095\2\u0689\u068f"+
		"\7\u009b\2\2\u068a\u068c\7{\2\2\u068b\u068d\7N\2\2\u068c\u068b\3\2\2\2"+
		"\u068c\u068d\3\2\2\2\u068d\u068e\3\2\2\2\u068e\u0690\7P\2\2\u068f\u068a"+
		"\3\2\2\2\u068f\u0690\3\2\2\2\u0690\u0696\3\2\2\2\u0691\u0692\7\u009a\2"+
		"\2\u0692\u0693\5\u0128\u0095\2\u0693\u0694\b\u0093\1\2\u0694\u0696\3\2"+
		"\2\2\u0695\u0687\3\2\2\2\u0695\u0691\3\2\2\2\u0696\u0698\3\2\2\2\u0697"+
		"\u0699\5\u0182\u00c2\2\u0698\u0697\3\2\2\2\u0698\u0699\3\2\2\2\u0699\u0125"+
		"\3\2\2\2\u069a\u069b\5\u0190\u00c9\2\u069b\u0127\3\2\2\2\u069c\u06a1\5"+
		"\u012a\u0096\2\u069d\u069e\7\u0098\2\2\u069e\u06a0\5\u012a\u0096\2\u069f"+
		"\u069d\3\2\2\2\u06a0\u06a3\3\2\2\2\u06a1\u069f\3\2\2\2\u06a1\u06a2\3\2"+
		"\2\2\u06a2\u0129\3\2\2\2\u06a3\u06a1\3\2\2\2\u06a4\u06a6\5\u00dan\2\u06a5"+
		"\u06a7\5\u0130\u0099\2\u06a6\u06a5\3\2\2\2\u06a6\u06a7\3\2\2\2\u06a7\u06ae"+
		"\3\2\2\2\u06a8\u06ae\5\u012c\u0097\2\u06a9\u06ab\5\u012e\u0098\2\u06aa"+
		"\u06ac\5\u0130\u0099\2\u06ab\u06aa\3\2\2\2\u06ab\u06ac\3\2\2\2\u06ac\u06ae"+
		"\3\2\2\2\u06ad\u06a4\3\2\2\2\u06ad\u06a8\3\2\2\2\u06ad\u06a9\3\2\2\2\u06ae"+
		"\u012b\3\2\2\2\u06af\u06b0\5\u00dan\2\u06b0\u06b1\7\u00a1\2\2\u06b1\u06b2"+
		"\7@\2\2\u06b2\u06b3\7\u009a\2\2\u06b3\u06b4\7\u009b\2\2\u06b4\u06c0\3"+
		"\2\2\2\u06b5\u06b6\7?\2\2\u06b6\u06b7\7\u009a\2\2\u06b7\u06b8\5\u00da"+
		"n\2\u06b8\u06b9\7\u009b\2\2\u06b9\u06c0\3\2\2\2\u06ba\u06bb\7@\2\2\u06bb"+
		"\u06bc\7\u009a\2\2\u06bc\u06bd\5\u00dan\2\u06bd\u06be\7\u009b\2\2\u06be"+
		"\u06c0\3\2\2\2\u06bf\u06af\3\2\2\2\u06bf\u06b5\3\2\2\2\u06bf\u06ba\3\2"+
		"\2\2\u06c0\u012d\3\2\2\2\u06c1\u06c2\5\u00dan\2\u06c2\u06c3\7\u00a1\2"+
		"\2\u06c3\u06c4\7x\2\2\u06c4\u06c5\7\u009a\2\2\u06c5\u06c6\7\u009b\2\2"+
		"\u06c6\u06d1\3\2\2\2\u06c7\u06c8\5\u00dan\2\u06c8\u06c9\7\u009c\2\2\u06c9"+
		"\u06ca\7\u009d\2\2\u06ca\u06d1\3\2\2\2\u06cb\u06cc\7\"\2\2\u06cc\u06cd"+
		"\7\u009a\2\2\u06cd\u06ce\5\u00dan\2\u06ce\u06cf\7\u009b\2\2\u06cf\u06d1"+
		"\3\2\2\2\u06d0\u06c1\3\2\2\2\u06d0\u06c7\3\2\2\2\u06d0\u06cb\3\2\2\2\u06d1"+
		"\u06d4\3\2\2\2\u06d2\u06d3\7\u00a1\2\2\u06d3\u06d5\5\u00dan\2\u06d4\u06d2"+
		"\3\2\2\2\u06d4\u06d5\3\2\2\2\u06d5\u012f\3\2\2\2\u06d6\u06e3\7\16\2\2"+
		"\u06d7\u06e4\7\u008a\2\2\u06d8\u06e4\7\u008b\2\2\u06d9\u06e4\7\u0086\2"+
		"\2\u06da\u06e4\7\u0090\2\2\u06db\u06e4\7\u0085\2\2\u06dc\u06e4\7\u008d"+
		"\2\2\u06dd\u06e4\7\u0093\2\2\u06de\u06e0\7\u0089\2\2\u06df\u06e1\5\u017a"+
		"\u00be\2\u06e0\u06df\3\2\2\2\u06e0\u06e1\3\2\2\2\u06e1\u06e4\3\2\2\2\u06e2"+
		"\u06e4\7\u008e\2\2\u06e3\u06d7\3\2\2\2\u06e3\u06d8\3\2\2\2\u06e3\u06d9"+
		"\3\2\2\2\u06e3\u06da\3\2\2\2\u06e3\u06db\3\2\2\2\u06e3\u06dc\3\2\2\2\u06e3"+
		"\u06dd\3\2\2\2\u06e3\u06de\3\2\2\2\u06e3\u06e2\3\2\2\2\u06e4\u0131\3\2"+
		"\2\2\u06e5\u06e6\7\27\2\2\u06e6\u06e7\7-\2\2\u06e7\u06eb\7\67\2\2\u06e8"+
		"\u06e9\7\64\2\2\u06e9\u06ea\7O\2\2\u06ea\u06ec\7\'\2\2\u06eb\u06e8\3\2"+
		"\2\2\u06eb\u06ec\3\2\2\2\u06ec\u06ed\3\2\2\2\u06ed\u06ee\5\u0126\u0094"+
		"\2\u06ee\u06ef\7S\2\2\u06ef\u06f0\5\u00ecw\2\u06f0\u06f2\5\u0134\u009b"+
		"\2\u06f1\u06f3\5\u013a\u009e\2\u06f2\u06f1\3\2\2\2\u06f2\u06f3\3\2\2\2"+
		"\u06f3\u06f5\3\2\2\2\u06f4\u06f6\7X\2\2\u06f5\u06f4\3\2\2\2\u06f5\u06f6"+
		"\3\2\2\2\u06f6\u06f8\3\2\2\2\u06f7\u06f9\5\u0182\u00c2\2\u06f8\u06f7\3"+
		"\2\2\2\u06f8\u06f9\3\2\2\2\u06f9\u0133\3\2\2\2\u06fa\u06fb\7\u009a\2\2"+
		"\u06fb\u06fc\5\u0136\u009c\2\u06fc\u06fd\7\u009b\2\2\u06fd\u0703\3\2\2"+
		"\2\u06fe\u06ff\7\u009a\2\2\u06ff\u0700\5\u0136\u009c\2\u0700\u0701\b\u009b"+
		"\1\2\u0701\u0703\3\2\2\2\u0702\u06fa\3\2\2\2\u0702\u06fe\3\2\2\2\u0703"+
		"\u0135\3\2\2\2\u0704\u0709\5\u0138\u009d\2\u0705\u0706\7\u0098\2\2\u0706"+
		"\u0708\5\u0138\u009d\2\u0707\u0705\3\2\2\2\u0708\u070b\3\2\2\2\u0709\u0707"+
		"\3\2\2\2\u0709\u070a\3\2\2\2\u070a\u0137\3\2\2\2\u070b\u0709\3\2\2\2\u070c"+
		"\u070e\5\u012a\u0096\2\u070d\u070f\5\u017a\u00be\2\u070e\u070d\3\2\2\2"+
		"\u070e\u070f\3\2\2\2\u070f\u0139\3\2\2\2\u0710\u0714\5\u013c\u009f\2\u0711"+
		"\u0713\5\u013c\u009f\2\u0712\u0711\3\2\2\2\u0713\u0716\3\2\2\2\u0714\u0712"+
		"\3\2\2\2\u0714\u0715\3\2\2\2\u0715\u013b\3\2\2\2\u0716\u0714\3\2\2\2\u0717"+
		"\u0718\7%\2\2\u0718\u0719\7\u00a8\2\2\u0719\u071e\7\u00b8\2\2\u071a\u071b"+
		"\7&\2\2\u071b\u071c\7\u00a8\2\2\u071c\u071e\7\u00b8\2\2\u071d\u0717\3"+
		"\2\2\2\u071d\u071a\3\2\2\2\u071e\u013d\3\2\2\2\u071f\u0720\7!\2\2\u0720"+
		"\u0723\7\67\2\2\u0721\u0722\7\64\2\2\u0722\u0724\7\'\2\2\u0723\u0721\3"+
		"\2\2\2\u0723\u0724\3\2\2\2\u0724\u0725\3\2\2\2\u0725\u0726\5\u0126\u0094"+
		"\2\u0726\u0727\7S\2\2\u0727\u0729\5\u00ecw\2\u0728\u072a\7X\2\2\u0729"+
		"\u0728\3\2\2\2\u0729\u072a\3\2\2\2\u072a\u013f\3\2\2\2\u072b\u072e\t\16"+
		"\2\2\u072c\u072d\7\16\2\2\u072d\u072f\7<\2\2\u072e\u072c\3\2\2\2\u072e"+
		"\u072f\3\2\2\2\u072f\u0741\3\2\2\2\u0730\u0731\7l\2\2\u0731\u073a\5\u00ec"+
		"w\2\u0732\u0733\7\u009a\2\2\u0733\u0734\5\u0142\u00a2\2\u0734\u0735\7"+
		"\u009b\2\2\u0735\u073b\3\2\2\2\u0736\u0737\7\u009a\2\2\u0737\u0738\5\u0142"+
		"\u00a2\2\u0738\u0739\b\u00a1\1\2\u0739\u073b\3\2\2\2\u073a\u0732\3\2\2"+
		"\2\u073a\u0736\3\2\2\2\u073a\u073b\3\2\2\2\u073b\u0742\3\2\2\2\u073c\u073d"+
		"\7\67\2\2\u073d\u073e\5\u0126\u0094\2\u073e\u073f\7S\2\2\u073f\u0740\5"+
		"\u00ecw\2\u0740\u0742\3\2\2\2\u0741\u0730\3\2\2\2\u0741\u073c\3\2\2\2"+
		"\u0742\u0141\3\2\2\2\u0743\u0748\5\u011c\u008f\2\u0744\u0745\7\u0098\2"+
		"\2\u0745\u0747\5\u011c\u008f\2\u0746\u0744\3\2\2\2\u0747\u074a\3\2\2\2"+
		"\u0748\u0746\3\2\2\2\u0748\u0749\3\2\2\2\u0749\u0143\3\2\2\2\u074a\u0748"+
		"\3\2\2\2\u074b\u074e\7j\2\2\u074c\u074d\7\16\2\2\u074d\u074f\7<\2\2\u074e"+
		"\u074c\3\2\2\2\u074e\u074f\3\2\2\2\u074f\u075e\3\2\2\2\u0750\u075f\7m"+
		"\2\2\u0751\u075f\7v\2\2\u0752\u075f\7d\2\2\u0753\u0754\7u\2\2\u0754\u075f"+
		"\5\u0154\u00ab\2\u0755\u0756\7c\2\2\u0756\u075f\5\u0190\u00c9\2\u0757"+
		"\u0758\78\2\2\u0758\u0759\7S\2\2\u0759\u075f\5\u00ecw\2\u075a\u075b\7"+
		"l\2\2\u075b\u075f\5\u00ecw\2\u075c\u075f\7L\2\2\u075d\u075f\7_\2\2\u075e"+
		"\u0750\3\2\2\2\u075e\u0751\3\2\2\2\u075e\u0752\3\2\2\2\u075e\u0753\3\2"+
		"\2\2\u075e\u0755\3\2\2\2\u075e\u0757\3\2\2\2\u075e\u075a\3\2\2\2\u075e"+
		"\u075c\3\2\2\2\u075e\u075d\3\2\2\2\u075f\u0145\3\2\2\2\u0760\u0761\7\27"+
		"\2\2\u0761\u0762\7u\2\2\u0762\u0764\5\u0158\u00ad\2\u0763\u0765\5\u0160"+
		"\u00b1\2\u0764\u0763\3\2\2\2\u0764\u0765\3\2\2\2\u0765\u0767\3\2\2\2\u0766"+
		"\u0768\7\b\2\2\u0767\u0766\3\2\2\2\u0767\u0768\3\2\2\2\u0768\u0147\3\2"+
		"\2\2\u0769\u076a\7\27\2\2\u076a\u076b\7c\2\2\u076b\u076c\5\u0190\u00c9"+
		"\2\u076c\u0149\3\2\2\2\u076d\u076e\7\n\2\2\u076e\u076f\7u\2\2\u076f\u0771"+
		"\5\u0154\u00ab\2\u0770\u0772\5\u015e\u00b0\2\u0771\u0770\3\2\2\2\u0771"+
		"\u0772\3\2\2\2\u0772\u0774\3\2\2\2\u0773\u0775\7\u0081\2\2\u0774\u0773"+
		"\3\2\2\2\u0774\u0775\3\2\2\2\u0775\u0777\3\2\2\2\u0776\u0778\7\177\2\2"+
		"\u0777\u0776\3\2\2\2\u0777\u0778\3\2\2\2\u0778\u077a\3\2\2\2\u0779\u077b"+
		"\5\u015c\u00af\2\u077a\u0779\3\2\2\2\u077a\u077b\3\2\2\2\u077b\u077d\3"+
		"\2\2\2\u077c\u077e\5\u0160\u00b1\2\u077d\u077c\3\2\2\2\u077d\u077e\3\2"+
		"\2\2\u077e\u014b\3\2\2\2\u077f\u0780\7!\2\2\u0780\u0781\7u\2\2\u0781\u0783"+
		"\5\u0154\u00ab\2\u0782\u0784\7\23\2\2\u0783\u0782\3\2\2\2\u0783\u0784"+
		"\3\2\2\2\u0784\u014d\3\2\2\2\u0785\u0786\7!\2\2\u0786\u0787\7c\2\2\u0787"+
		"\u0788\5\u0190\u00c9\2\u0788\u014f\3\2\2\2\u0789\u078d\7/\2\2\u078a\u078e"+
		"\5\u0162\u00b2\2\u078b\u078e\5\u0164\u00b3\2\u078c\u078e\5\u0166\u00b4"+
		"\2\u078d\u078a\3\2\2\2\u078d\u078b\3\2\2\2\u078d\u078c\3\2\2\2\u078e\u0151"+
		"\3\2\2\2\u078f\u0793\7b\2\2\u0790\u0794\5\u0168\u00b5\2\u0791\u0794\5"+
		"\u016a\u00b6\2\u0792\u0794\5\u016c\u00b7\2\u0793\u0790\3\2\2\2\u0793\u0791"+
		"\3\2\2\2\u0793\u0792\3\2\2\2\u0794\u0153\3\2\2\2\u0795\u0798\5\u0190\u00c9"+
		"\2\u0796\u0798\5\u018c\u00c7\2\u0797\u0795\3\2\2\2\u0797\u0796\3\2\2\2"+
		"\u0798\u0155\3\2\2\2\u0799\u079a\7\62\2\2\u079a\u079b\5\u015a\u00ae\2"+
		"\u079b\u0157\3\2\2\2\u079c\u079d\5\u0190\u00c9\2\u079d\u079f\5\u0156\u00ac"+
		"\2\u079e\u07a0\7\177\2\2\u079f\u079e\3\2\2\2\u079f\u07a0\3\2\2\2\u07a0"+
		"\u07a2\3\2\2\2\u07a1\u07a3\5\u015c\u00af\2\u07a2\u07a1\3\2\2\2\u07a2\u07a3"+
		"\3\2\2\2\u07a3\u07a8\3\2\2\2\u07a4\u07a5\5\u018c\u00c7\2\u07a5\u07a6\7"+
		"~\2\2\u07a6\u07a8\3\2\2\2\u07a7\u079c\3\2\2\2\u07a7\u07a4\3\2\2\2\u07a8"+
		"\u0159\3\2\2\2\u07a9\u07aa\7\20\2\2\u07aa\u07ab\5\u018c\u00c7\2\u07ab"+
		"\u015b\3\2\2\2\u07ac\u07ad\7Y\2\2\u07ad\u07ae\7C\2\2\u07ae\u07af\5\u0184"+
		"\u00c3\2\u07af\u015d\3\2\2\2\u07b0\u07b2\5\u0156\u00ac\2\u07b1\u07b3\7"+
		"\u0080\2\2\u07b2\u07b1\3\2\2\2\u07b2\u07b3\3\2\2\2\u07b3\u015f\3\2\2\2"+
		"\u07b4\u07b5\7\6\2\2\u07b5\u07b6\t\17\2\2\u07b6\u0161\3\2\2\2\u07b7\u07b8"+
		"\5\u018e\u00c8\2\u07b8\u07b9\7o\2\2\u07b9\u07ba\5\u016e\u00b8\2\u07ba"+
		"\u0163\3\2\2\2\u07bb\u07bc\5\u0170\u00b9\2\u07bc\u07bd\7o\2\2\u07bd\u07be"+
		"\5\u0190\u00c9\2\u07be\u0165\3\2\2\2\u07bf\u07c0\5\u0174\u00bb\2\u07c0"+
		"\u07c4\7S\2\2\u07c1\u07c5\5\u0176\u00bc\2\u07c2\u07c3\7K\2\2\u07c3\u07c5"+
		"\5\u00eex\2\u07c4\u07c1\3\2\2\2\u07c4\u07c2\3\2\2\2\u07c5\u07c6\3\2\2"+
		"\2\u07c6\u07c7\7o\2\2\u07c7\u07c8\5\u0190\u00c9\2\u07c8\u0167\3\2\2\2"+
		"\u07c9\u07ca\5\u018e\u00c8\2\u07ca\u07cb\7,\2\2\u07cb\u07cc\5\u016e\u00b8"+
		"\2\u07cc\u0169\3\2\2\2\u07cd\u07ce\5\u0170\u00b9\2\u07ce\u07cf\7,\2\2"+
		"\u07cf\u07d0\5\u0190\u00c9\2\u07d0\u016b\3\2\2\2\u07d1\u07d2\5\u0174\u00bb"+
		"\2\u07d2\u07d6\7S\2\2\u07d3\u07d7\5\u0176\u00bc\2\u07d4\u07d5\7K\2\2\u07d5"+
		"\u07d7\5\u00eex\2\u07d6\u07d3\3\2\2\2\u07d6\u07d4\3\2\2\2\u07d7\u07d8"+
		"\3\2\2\2\u07d8\u07d9\7,\2\2\u07d9\u07da\5\u0190\u00c9\2\u07da\u016d\3"+
		"\2\2\2\u07db\u07dc\7u\2\2\u07dc\u07e0\5\u0154\u00ab\2\u07dd\u07de\7c\2"+
		"\2\u07de\u07e0\5\u0190\u00c9\2\u07df\u07db\3\2\2\2\u07df\u07dd\3\2\2\2"+
		"\u07e0\u016f\3\2\2\2\u07e1\u07e6\5\u0172\u00ba\2\u07e2\u07e3\7\u0098\2"+
		"\2\u07e3\u07e5\5\u0172\u00ba\2\u07e4\u07e2\3\2\2\2\u07e5\u07e8\3\2\2\2"+
		"\u07e6\u07e4\3\2\2\2\u07e6\u07e7\3\2\2\2\u07e7\u0171\3\2\2\2\u07e8\u07e6"+
		"\3\2\2\2\u07e9\u07ec\5\u0190\u00c9\2\u07ea\u07ec\7}\2\2\u07eb\u07e9\3"+
		"\2\2\2\u07eb\u07ea\3\2\2\2\u07ec\u0173\3\2\2\2\u07ed\u07f0\5\u0172\u00ba"+
		"\2\u07ee\u07f0\7\t\2\2\u07ef\u07ed\3\2\2\2\u07ef\u07ee\3\2\2\2\u07f0\u07f8"+
		"\3\2\2\2\u07f1\u07f4\7\u0098\2\2\u07f2\u07f5\5\u0172\u00ba\2\u07f3\u07f5"+
		"\7\t\2\2\u07f4\u07f2\3\2\2\2\u07f4\u07f3\3\2\2\2\u07f5\u07f7\3\2\2\2\u07f6"+
		"\u07f1\3\2\2\2\u07f7\u07fa\3\2\2\2\u07f8\u07f6\3\2\2\2\u07f8\u07f9\3\2"+
		"\2\2\u07f9\u0175\3\2\2\2\u07fa\u07f8\3\2\2\2\u07fb\u07fc\5\u00ecw\2\u07fc"+
		"\u0177\3\2\2\2\u07fd\u0800\5\u017a\u00be\2\u07fe\u0800\5\u017c\u00bf\2"+
		"\u07ff\u07fd\3\2\2\2\u07ff\u07fe\3\2\2\2\u0800\u0179\3\2\2\2\u0801\u0802"+
		"\7\u009e\2\2\u0802\u0807\5\u017e\u00c0\2\u0803\u0804\7\u0098\2\2\u0804"+
		"\u0806\5\u017e\u00c0\2\u0805\u0803\3\2\2\2\u0806\u0809\3\2\2\2\u0807\u0805"+
		"\3\2\2\2\u0807\u0808\3\2\2\2\u0808\u080a\3\2\2\2\u0809\u0807\3\2\2\2\u080a"+
		"\u080b\7\u009f\2\2\u080b\u080f\3\2\2\2\u080c\u080d\7\u009e\2\2\u080d\u080f"+
		"\7\u009f\2\2\u080e\u0801\3\2\2\2\u080e\u080c\3\2\2\2\u080f\u017b\3\2\2"+
		"\2\u0810\u0811\7\u009c\2\2\u0811\u0816\5\u0180\u00c1\2\u0812\u0813\7\u0098"+
		"\2\2\u0813\u0815\5\u0180\u00c1\2\u0814\u0812\3\2\2\2\u0815\u0818\3\2\2"+
		"\2\u0816\u0814\3\2\2\2\u0816\u0817\3\2\2\2\u0817\u0819\3\2\2\2\u0818\u0816"+
		"\3\2\2\2\u0819\u081a\7\u009d\2\2\u081a\u081e\3\2\2\2\u081b\u081c\7\u009c"+
		"\2\2\u081c\u081e\7\u009d\2\2\u081d\u0810\3\2\2\2\u081d\u081b\3\2\2\2\u081e"+
		"\u017d\3\2\2\2\u081f\u0820\7\u00bb\2\2\u0820\u0821\7\u0099\2\2\u0821\u0822"+
		"\5\u0180\u00c1\2\u0822\u017f\3\2\2\2\u0823\u082b\5\u017a\u00be\2\u0824"+
		"\u082b\5\u017c\u00bf\2\u0825\u082b\7\u00bb\2\2\u0826\u082b\5\u0188\u00c5"+
		"\2\u0827\u082b\7\u00b7\2\2\u0828\u082b\7\u00b6\2\2\u0829\u082b\7\u00b5"+
		"\2\2\u082a\u0823\3\2\2\2\u082a\u0824\3\2\2\2\u082a\u0825\3\2\2\2\u082a"+
		"\u0826\3\2\2\2\u082a\u0827\3\2\2\2\u082a\u0828\3\2\2\2\u082a\u0829\3\2"+
		"\2\2\u082b\u0181\3\2\2\2\u082c\u082d\7\25\2\2\u082d\u082e\5\u018c\u00c7"+
		"\2\u082e\u0183\3\2\2\2\u082f\u0830\7\u00b8\2\2\u0830\u0831\5\u0186\u00c4"+
		"\2\u0831\u0185\3\2\2\2\u0832\u0833\t\20\2\2\u0833\u0187\3\2\2\2\u0834"+
		"\u0836\7\u00b1\2\2\u0835\u0834\3\2\2\2\u0835\u0836\3\2\2\2\u0836\u0837"+
		"\3\2\2\2\u0837\u0838\t\21\2\2\u0838\u0189\3\2\2\2\u0839\u083b\t\5\2\2"+
		"\u083a\u0839\3\2\2\2\u083a\u083b\3\2\2\2\u083b\u083c\3\2\2\2\u083c\u083d"+
		"\7\u00b8\2\2\u083d\u018b\3\2\2\2\u083e\u083f\t\22\2\2\u083f\u018d\3\2"+
		"\2\2\u0840\u0845\5\u0190\u00c9\2\u0841\u0842\7\u0098\2\2\u0842\u0844\5"+
		"\u0190\u00c9\2\u0843\u0841\3\2\2\2\u0844\u0847\3\2\2\2\u0845\u0843\3\2"+
		"\2\2\u0845\u0846\3\2\2\2\u0846\u018f\3\2\2\2\u0847\u0845\3\2\2\2\u0848"+
		"\u084c\t\23\2\2\u0849\u084a\7\u00bf\2\2\u084a\u084c\b\u00c9\1\2\u084b"+
		"\u0848\3\2\2\2\u084b\u0849\3\2\2\2\u084c\u0191\3\2\2\2\u00ea\u01ad\u01b0"+
		"\u01bc\u01c7\u01ca\u01cd\u01d0\u01d3\u01d9\u01de\u01e4\u01f0\u01f7\u0200"+
		"\u0208\u0210\u0219\u021d\u0220\u0224\u022d\u0230\u023b\u023e\u0244\u024f"+
		"\u0264\u0267\u026b\u0277\u027b\u027f\u0288\u0299\u02a4\u02a8\u02af\u02b2"+
		"\u02b8\u02bd\u02c1\u02cb\u02d0\u02da\u02e4\u02ef\u02fc\u0307\u030c\u0317"+
		"\u031b\u031f\u0324\u0329\u0333\u033b\u0343\u0349\u034e\u0350\u0356\u035d"+
		"\u0362\u0368\u036c\u0370\u0376\u0386\u038b\u0392\u0398\u039e\u03ae\u03b5"+
		"\u03c7\u03ca\u03df\u03e4\u03fb\u0401\u0404\u040c\u0411\u041a\u0421\u0424"+
		"\u042b\u0433\u0436\u043b\u043e\u0445\u044b\u0455\u0459\u0461\u0465\u046d"+
		"\u0471\u047a\u0484\u0487\u048f\u049e\u04a5\u04ab\u04ae\u04b2\u04b5\u04bc"+
		"\u04cd\u04d6\u04de\u04e1\u04e5\u04e9\u04eb\u04f3\u0514\u051c\u0522\u0531"+
		"\u0539\u053d\u0546\u054b\u0552\u055a\u055e\u0574\u0578\u0582\u058a\u058f"+
		"\u0593\u059b\u059e\u05a5\u05a7\u05aa\u05b7\u05be\u05c3\u05ca\u05cd\u05d0"+
		"\u05d3\u05d5\u05ef\u05f1\u05f9\u05fd\u0614\u061b\u0626\u062c\u0632\u0636"+
		"\u0641\u0644\u064d\u0650\u0656\u065d\u0665\u066d\u0673\u0679\u0682\u068c"+
		"\u068f\u0695\u0698\u06a1\u06a6\u06ab\u06ad\u06bf\u06d0\u06d4\u06e0\u06e3"+
		"\u06eb\u06f2\u06f5\u06f8\u0702\u0709\u070e\u0714\u071d\u0723\u0729\u072e"+
		"\u073a\u0741\u0748\u074e\u075e\u0764\u0767\u0771\u0774\u0777\u077a\u077d"+
		"\u0783\u078d\u0793\u0797\u079f\u07a2\u07a7\u07b2\u07c4\u07d6\u07df\u07e6"+
		"\u07eb\u07ef\u07f4\u07f8\u07ff\u0807\u080e\u0816\u081d\u082a\u0835\u083a"+
		"\u0845\u084b";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}