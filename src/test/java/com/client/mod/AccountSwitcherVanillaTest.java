package com.client.mod;

public class AccountSwitcherVanillaTest {

    public static void main(String[] args) throws Exception {
        testIasBridgeNullSafety();
        testAutoLoginConfigStorage();
        testScreenClassesLoadable();
        System.out.println("ALL AccountSwitcherVanillaTest ASSERTIONS PASSED!");
    }

    private static void testIasBridgeNullSafety() {
        assert !IasBridge.tryReplaceIasScreen(null) : "null screen should not be replaced";
        assert "ias.error".equals(IasBridge.extractFriendlyErrorKey(null));
        assert "ias.error".equals(IasBridge.extractFriendlyErrorKey(new RuntimeException("x")));
        assert "Unknown".equals(IasBridge.accountName(null));
        assert !IasBridge.canLogin(null);
        assert !IasBridge.isInsecure(null);
        assert !IasBridge.isMicrosoftAccount(null);
        assert IasBridge.accountUuid(null) == null;
    }

    private static void testAutoLoginConfigStorage() {
        ModConfig cfg = new ModConfig();
        cfg.accountPasswords.put("kheromie1", "secret123");
        ModConfig copy = new ModConfig();
        copy.copyFrom(cfg);
        assert "secret123".equals(copy.accountPasswords.get("kheromie1"));
        copy.sanitize();
        assert "secret123".equals(copy.accountPasswords.get("kheromie1"));
    }

    private static void testScreenClassesLoadable() throws ClassNotFoundException {
        ClassLoader cl = AccountSwitcherVanillaTest.class.getClassLoader();
        for (String name : new String[]{
                "com.client.mod.SestiaAccountScreen",
                "com.client.mod.SestiaAddAccountScreen",
                "com.client.mod.SestiaLoginScreen",
                "com.client.mod.SestiaDeleteAccountScreen",
                "com.client.mod.SestiaMicrosoftCryptScreen",
                "com.client.mod.SestiaMicrosoftAuthScreen"
        }) {
            Class<?> cls = Class.forName(name, false, cl);
            assert cls != null;
        }
    }
}
