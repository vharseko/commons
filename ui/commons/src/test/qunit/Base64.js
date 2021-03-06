/**
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

define([
    "org/forgerock/commons/ui/common/util/Base64",
    "org/forgerock/commons/ui/common/util/Mime"
], function (Base64, Mime) {
    QUnit.module('Base64 Functions');

    QUnit.test("Base64.encodeUTF8", function () {
        var input = "パスワードパスワード";

        QUnit.strictEqual(Base64.encodeUTF8(input), "44OR44K544Ov44O844OJ44OR44K544Ov44O844OJ",
            "Incorrect base-64 encoding");
    });

    QUnit.test("Base64.encodeUTF8 - 2 pad chars", function() {
        var input = "パスワードパスワードx";

        QUnit.strictEqual(Base64.encodeUTF8(input), "44OR44K544Ov44O844OJ44OR44K544Ov44O844OJeA==",
            "Incorrect base-64 encoding - 2 pad char case");
    });

    QUnit.test("Base64.encodeUTF8 - 1 pad char", function() {
        var input = "パスワードパスワードxx";

        QUnit.strictEqual(Base64.encodeUTF8(input), "44OR44K544Ov44O844OJ44OR44K544Ov44O844OJeHg=",
            "Incorrect base-64 encoding - 1 pad char case");
    });

    QUnit.test("Base64.decodeUTF8", function() {
        var input = "44OR44K544Ov44O844OJ44OR44K544Ov44O844OJ";

        QUnit.strictEqual(Base64.decodeUTF8(input), "パスワードパスワード",
            "Incorrect base-64 decoding");
    });
    QUnit.test("Base64.decodeUTF8 - 1 pad char", function() {
        var input = "44OR44K544Ov44O844OJ44OR44K544Ov44O844OJeHg=";
        QUnit.strictEqual(Base64.decodeUTF8(input), "パスワードパスワードxx",
            "Incorrect base-64 decoding");
    });

    QUnit.test("Base64.decodeUTF8 - 2 pad chars", function() {
        var input = "44OR44K544Ov44O844OJ44OR44K544Ov44O844OJeA==";
        QUnit.strictEqual(Base64.decodeUTF8(input), "パスワードパスワードx",
            "Incorrect base-64 decoding");
    });

    QUnit.test("Base64.encodeUTF8/decodeUTF8 - various punctuation characters", function() {
        var input = "43uin 98e2 + 343_ {} 43qafdgfREER\'FDj ionk/.,<>`fj iod Hdfjl";

        QUnit.strictEqual(Base64.decodeUTF8(Base64.encodeUTF8(input)), input,
            "Unable to round-trip Base64 special characters");
    });

    QUnit.test("Mime.encodeHeader", function() {
        var input = "パスワードパスワード";

        QUnit.strictEqual(Mime.encodeHeader(input), "=?UTF-8?B?44OR44K544Ov44O844OJ44OR44K544Ov44O844OJ?=",
            "Incorrect Mime encoding in header");
    });
});
