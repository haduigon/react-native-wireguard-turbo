"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.getState = exports.disconnect = exports.connect = exports.ping = void 0;
const wireguard_1 = __importDefault(require("./wireguard"));
exports.default = wireguard_1.default;
const ping = () => wireguard_1.default.ping();
exports.ping = ping;
const connect = (config) => wireguard_1.default.connect(config);
exports.connect = connect;
const disconnect = () => wireguard_1.default.disconnect();
exports.disconnect = disconnect;
const getState = () => wireguard_1.default.getState();
exports.getState = getState;
