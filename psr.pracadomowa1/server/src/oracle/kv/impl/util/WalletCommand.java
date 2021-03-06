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

package oracle.kv.impl.util;

import static oracle.kv.impl.security.PasswordManager.WALLET_MANAGER_CLASS;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import oracle.kv.impl.security.PasswordManager;
import oracle.kv.impl.security.PasswordStore;
import oracle.kv.impl.security.PasswordStore.LoginId;
import oracle.kv.impl.security.PasswordStoreException;
import oracle.kv.impl.security.util.PasswordReader;
import oracle.kv.impl.security.util.SecurityUtils;
import oracle.kv.util.shell.CommandWithSubs;
import oracle.kv.util.shell.Shell;
import oracle.kv.util.shell.ShellException;

/**
 * WalletCommand provides Oracle Wallet-specific PasswordStore manipulation.
 */
class WalletCommand extends CommandWithSubs {

    private enum Action {
        SET, DELETE, LIST;
    }

    WalletCommand() {
        super(Arrays.asList(new WalletCreate(),
                            new WalletLogin(),  /* currently hidden */
                            new WalletSecret(),
                            new WalletPassphrase()),  /* currently hidden */
              "wallet", 4, 1);
    }

    @Override
    public String getCommandOverview() {
        return "The wallet command allows creation and modification of " +
            "an Oracle wallet.";
    }

    /**
     * WalletCreate - implements the "wallet create" subcommand
     */
    private static final class WalletCreate extends SubCommand {
        private static final String CREATE_COMMAND_NAME = "create";
        private static final String CREATE_COMMAND_DESC =
            "Creates a new Oracle Wallet.";
        private static final String CREATE_COMMAND_ARGS =
            "-directory <wallet directory>";

        private WalletCreate() {
            super(CREATE_COMMAND_NAME, 3);
        }

        @Override
        public String execute(String[] args, Shell shell)
            throws ShellException {

            Shell.checkHelp(args, this);

            String dir = null;
            final boolean autologin = true;

            for (int i = 1; i < args.length; i++) {
                final String arg = args[i];
                if ("-directory".equals(arg) ||
                    (arg.startsWith("-dir") && "-directory".startsWith(arg))) {
                    dir = Shell.nextArg(args, i++, this);
                } else {
                    shell.unknownArgument(arg, this);
                }
            }
            if (dir == null) {
                shell.requiredArg("-directory", this);
            }

            return doCreate(dir, autologin, shell);
        }

        private String doCreate(String dir, boolean autologin, Shell shell)
            throws ShellException {

            final SecurityShell secShell = (SecurityShell) shell;

            try {
                final PasswordManager pwdMgr = PasswordManager.load(
                    WALLET_MANAGER_CLASS);
                final File walletLoc = new File(dir);
                final PasswordStore wallet = pwdMgr.getStoreHandle(walletLoc);
                if (wallet.exists()) {
                    return "A wallet already exists at that location";
                }

                char[] pwd = null;
                if (!autologin) {
                    final PasswordReader pwdReader =
                        secShell.getPasswordReader();
                    pwd = pwdReader.readPassword(
                        "Enter a passphrase to protect the wallet: ");
                    if (!wallet.isValidPassphrase(pwd)) {
                        return "The specified password is not acceptable.";
                    }
                    final char[] pwd2 = pwdReader.readPassword(
                        "Re-enter the passphrase for verification: ");
                    if (!SecurityUtils.passwordsMatch(pwd, pwd2)) {
                        return "The two passphrases do not match.";
                    }
                }

                wallet.create(pwd);
                return "Created";
            } catch (PasswordStoreException pwse) {
                throw new ShellException(
                    "PasswordStore error: " + pwse.getMessage(), pwse);
            } catch (Exception e) {
                throw new ShellException(
                    "Unknown error: " + e.getMessage(), e);
            }
        }

        @Override
        public String getCommandSyntax() {
            return "wallet create " + CREATE_COMMAND_ARGS;
        }

        @Override
        public String getCommandDescription() {
            return CREATE_COMMAND_DESC;
        }
    }

    /**
     * WalletSecret - implements the "wallet secret" subcommand.
     */
    private static final class WalletSecret extends SubCommand {

        private static final String SECRET_COMMAND_NAME = "secret";
        private static final String SECRET_COMMAND_DESC =
            "Manipulate secrets in an Oracle Wallet.";
        private static final String SECRET_COMMAND_ARGS =
            "-directory <wallet directory> " +
            "{-set [-secret <secret>] | -delete -alias <aliasname>} | " +
            "{-list}";

        private WalletSecret() {
            super(SECRET_COMMAND_NAME, 3);
        }

        @Override
        public String execute(String[] args, Shell shell)
            throws ShellException {

            String dir = null;
            String alias = null;
            String secret = null;
            final EnumSet<Action> actions = EnumSet.noneOf(Action.class);

            for (int i = 1; i < args.length; i++) {
                final String arg = args[i];
                if ("-directory".equals(arg) || "-dir".equals(arg)) {
                    dir = Shell.nextArg(args, i++, this);
                } else if ("-alias".equals(arg)) {
                    alias = Shell.nextArg(args, i++, this);
                } else if ("-set".equals(arg)) {
                    actions.add(Action.SET);
                } else if ("-list".equals(arg)) {
                    actions.add(Action.LIST);
                } else if ("-delete".equals(arg)) {
                    actions.add(Action.DELETE);
                } else if ("-secret".equals(arg)) {
                    secret = Shell.nextArg(args, i++, this);
                } else {
                    shell.unknownArgument(arg, this);
                }
            }

            if (dir == null || actions.size() != 1) {
                shell.badArgCount(this);
            }

            final Action action = actions.iterator().next();

            if ((action == Action.SET || action == Action.DELETE) &&
                alias == null) {
                shell.badArgCount(this);
            }

            if (action == Action.LIST && alias != null) {
                shell.badArgCount(this);
            }

            return doSecret(dir, action, alias, secret, shell);
        }

        @Override
        public String getCommandSyntax() {
            return "wallet secret " + SECRET_COMMAND_ARGS;
        }
        @Override
        public String getCommandDescription() {
            return SECRET_COMMAND_DESC;
        }

        /**
         * Does the work for the "wallet secret" command.
         */
        private String doSecret(String dir,
                                Action action,
                                String alias,
                                String secretArg,
                                Shell shell)
            throws ShellException {

            try {
                final PasswordStore wallet = openStore(new File(dir), shell);
                final PasswordReader pwdReader =
                    ((SecurityShell) shell).getPasswordReader();
                final String retString;

                if (action == Action.SET) {
                    char[] secret;
                    char[] verifySecret;

                    if (secretArg != null) {
                        secret = secretArg.toCharArray();
                        verifySecret = secret;
                    } else {
                        secret = pwdReader.readPassword(
                            "Enter the secret value to store: ");
                        verifySecret = pwdReader.readPassword(
                            "Re-enter the secret value for verification: ");
                    }

                    if (SecurityUtils.passwordsMatch(secret, verifySecret)) {
                        if (wallet.setSecret(alias, secret)) {
                            retString = "Secret updated";
                        } else {
                            retString = "Secret created";
                        }
                        wallet.save();
                    } else {
                        retString = "The passwords do not match";
                    }
                } else if (action == Action.DELETE) {
                    if (wallet.deleteSecret(alias)) {
                        wallet.save();
                        retString = "Secret deleted";
                    } else {
                        retString = "Secret did not exist";
                    }
                } else if (action == Action.LIST) {
                    final Collection<String> secretAliases =
                        wallet.getSecretAliases();
                    if (secretAliases.size() == 0) {
                        retString = "The wallet contains no secrets";
                    } else {
                        final StringBuilder sb = new StringBuilder();
                        sb.append("The wallet contains the following secrets:");
                        for (String s : secretAliases) {
                            sb.append(eol);
                            sb.append("   ");
                            sb.append(s);
                        }
                        retString = sb.toString();
                    }
                }
                else {
                    throw new AssertionError("Unhandled action " + action);
                }
                return retString;

            } catch (ShellException se) {
                throw se;
            } catch (PasswordStoreException pwse) {
                throw new ShellException(
                    "PasswordStore error: " + pwse.getMessage(), pwse);
            } catch (Exception e) {
                throw new ShellException(
                    "Unknown error: " + e.getMessage(), e);
            }
        }
    }

    /**
     * WalletLogin - implements the "wallet login" subcommand.
     * Not currently exposed.
     */
    private static final class WalletLogin extends SubCommand {
        private static final String LOGIN_COMMAND_NAME = "login";
        private static final String LOGIN_COMMAND_DESC =
            "Manipulate logins in an Oracle Wallet.";
        private static final String LOGIN_COMMAND_ARGS =
            "-directory <wallet directory> " +
            "{-set [-secret <secret>] -database <db> -user <username>} | " +
            "{-delete -database <db> -user <username>} | " +
            "{-list}";

        private WalletLogin() {
            super(LOGIN_COMMAND_NAME, 3);
        }

        @Override
        public String execute(String[] args, Shell shell)
            throws ShellException {

            String dir = null;
            String user = null;
            String db = null;
            String secret = null;
            final EnumSet<Action> actions = EnumSet.noneOf(Action.class);

            for (int i = 1; i < args.length; i++) {
                final String arg = args[i];
                if ("-directory".equals(arg) || "-dir".equals(arg)) {
                    dir = Shell.nextArg(args, i++, this);
                } else if ("-database".equals(arg) || "-db".equals(arg)) {
                    db = Shell.nextArg(args, i++, this);
                } else if ("-user".equals(arg)) {
                    user = Shell.nextArg(args, i++, this);
                } else if ("-set".equals(arg)) {
                    actions.add(Action.SET);
                } else if ("-list".equals(arg)) {
                    actions.add(Action.LIST);
                } else if ("-delete".equals(arg)) {
                    actions.add(Action.DELETE);
                } else if ("-secret".equals(arg)) {
                    secret = Shell.nextArg(args, i++, this);
                } else {
                    shell.unknownArgument(arg, this);
                }
            }

            if (dir == null || actions.size() != 1) {
                shell.badArgCount(this);
            }

            final Action action = actions.iterator().next();

            if ((action == Action.SET || action == Action.DELETE) &&
                (db == null)) {
                shell.badArgCount(this);
            }

            if ((action == Action.SET) &&
                (user == null)) {
                shell.badArgCount(this);
            }

            if (action == Action.LIST && (db != null || user != null)) {
                shell.badArgCount(this);
            }
            return doLogin(dir, action, user, db, secret, shell);
        }

        @Override
        public String getCommandSyntax() {
            return "wallet login " + LOGIN_COMMAND_ARGS;
        }

        @Override
        public String getCommandDescription() {
            return LOGIN_COMMAND_DESC;
        }

        /* Not visible currently */
        @Override
        protected boolean isHidden() {
            return true;
        }

        /**
         * Does the work for the "wallet login" command.
         */
        private String doLogin(String dir,
                               Action action,
                               String user,
                               String db,
                               String secretArg,
                               Shell shell)
            throws ShellException {

            try {
                final PasswordStore wallet = openStore(new File(dir), shell);
                final PasswordReader pwdReader =
                    ((SecurityShell) shell).getPasswordReader();
                final LoginId loginId = new LoginId(db, user);
                final String retString;

                if (action == Action.SET) {
                    char[] secret;
                    char[] verifySecret;
                    if (secretArg != null) {
                        secret = secretArg.toCharArray();
                        verifySecret = secret;
                    } else {
                        secret = pwdReader.readPassword(
                            "Enter the secret value to store: ");
                        verifySecret = pwdReader.readPassword(
                            "Re-enter the secret value for verification: ");
                    }

                    if (SecurityUtils.passwordsMatch(secret, verifySecret)) {
                        if (wallet.setLogin(loginId, secret)) {
                            retString = "Login updated";
                        } else {
                            retString = "Login created";
                        }
                        wallet.save();
                    } else {
                        retString = "The passwords do not match";
                    }
                } else if (action == Action.DELETE) {

                    /*
                     * If the user specified a user (not required), check
                     * that that the user in the entry matches the specified
                     * user before deleting.
                     */
                    final LoginId walletLoginId =
                        wallet.getLoginId(loginId.getDatabase());

                    if (walletLoginId != null) {
                        if (loginId.getUser() == null ||
                            loginId.getUser().equals(
                                walletLoginId.getUser())) {
                            wallet.deleteLogin(loginId.getDatabase());
                            wallet.save();
                            retString = "Login deleted";
                        } else {
                            retString = "The specified user does not match " +
                                    "the wallet entry";
                        }
                    } else {
                        retString = "Login did not exist";
                    }
                } else if (action == Action.LIST) {
                    final Collection<LoginId> logins = wallet.getLogins();
                    if (logins.size() == 0) {
                        retString = "The wallet contains no logins";
                    } else {
                        final StringBuilder sb = new StringBuilder();
                        sb.append("The wallet contains the following logins:");
                        for (LoginId lid : logins) {
                            sb.append(eol);
                            sb.append("   ");
                            sb.append(lid.getDatabase());
                            sb.append(" as ");
                            sb.append(lid.getUser());
                        }
                        retString = sb.toString();
                    }
                }
                else {
                    throw new AssertionError("Unhandled action " + action);
                }
                return retString;
            } catch (ShellException se) {
                throw se;
            } catch (PasswordStoreException pwse) {
                throw new ShellException(
                    "PasswordStore error: " + pwse.getMessage(), pwse);
            } catch (Exception e) {
                throw new ShellException(
                    "Unknown error: " + e.getMessage(), e);
            }
        }
    }

    /**
     * WalletPassphrase - implements the "wallet passphrase" subcommand
     */
    private static final class WalletPassphrase extends SubCommand {
        private static final String PASSPHRASE_COMMAND_NAME = "passphrase";
        private static final String PASSPHRASE_COMMAND_DESC =
            "Modify passphrase for an Oracle Wallet.";
        private static final String PASSPHRASE_COMMAND_ARGS =
            "-directory <wallet directory> [-autologin]";

        private WalletPassphrase() {
            super(PASSPHRASE_COMMAND_NAME, 3);
        }

        @Override
        public String execute(String[] args, Shell shell)
            throws ShellException {

            String dir = null;
            boolean autologin = false;
            for (int i = 1; i < args.length; i++) {
                final String arg = args[i];
                if ("-directory".equals(arg) ||
                    (arg.startsWith("-dir") &&
                     "-directory".startsWith(arg))) {
                    dir = Shell.nextArg(args, i++, this);
                } else if ("-autologin".equals(arg) ||
                           (arg.startsWith("-auto") &&
                            "-autologin".startsWith(arg))) {
                    autologin = true;
                } else {
                    shell.unknownArgument(arg, this);
                }
            }
            if (dir == null) {
                shell.badArgCount(this);
            }
            return doPassphrase(dir, autologin, shell);
        }

        @Override
        public String getCommandSyntax() {
            return "wallet passphrase " + PASSPHRASE_COMMAND_ARGS;
        }

        @Override
        public String getCommandDescription() {
            return PASSPHRASE_COMMAND_DESC;
        }

        /* Not visible currently */
        @Override
        protected boolean isHidden() {
            return true;
        }

        private String doPassphrase(String dir, boolean autologin,
                                    Shell shell)
            throws ShellException {

            try {
                final PasswordReader pwdReader =
                    ((SecurityShell) shell).getPasswordReader();
                final PasswordStore wallet = openStore(new File(dir), shell);
                final String retString;

                if (!autologin) {
                    final char[] passphrase = pwdReader.readPassword(
                        "Enter a new passphrase for the  store: ");
                    if (!wallet.isValidPassphrase(passphrase)) {
                        retString = "That is not a valid passphrase";
                    } else {
                        final char[] verifyPassphrase = pwdReader.readPassword(
                            "Re-enter the passphrase for verification: ");
                        if (SecurityUtils.passwordsMatch(
                                passphrase, verifyPassphrase)) {
                            wallet.setPassphrase(passphrase);
                            retString = "Passphrase set";
                        } else {
                            retString = "The passwords do not match";
                        }
                    }
                } else {
                    wallet.setPassphrase(null);
                    retString = "Set to autologin";
                }
                return retString;
            } catch (ShellException se) {
                throw se;
            } catch (PasswordStoreException pwse) {
                throw new ShellException(
                    "PasswordStore error: " + pwse.getMessage(), pwse);
            } catch (Exception e) {
                throw new ShellException(
                    "Unknown error: " + e.getMessage(), e);
            }
        }
    }

    private static PasswordStore openStore(File walletLoc, Shell shell)
        throws Exception {

        final PasswordManager pwdMgr =
            PasswordManager.load(PasswordManager.WALLET_MANAGER_CLASS);
        final PasswordStore wallet = pwdMgr.getStoreHandle(walletLoc);
        final PasswordReader pwdReader =
            ((SecurityShell) shell).getPasswordReader();

        if (!wallet.exists()) {
            throw new ShellException("The store does not yet exist");
        }
        char[] pwd = null;
        if (wallet.requiresPassphrase()) {
            pwd = pwdReader.readPassword(
                "Enter the current wallet passphrase: ");
        }
        wallet.open(pwd);
        return wallet;
    }
}
