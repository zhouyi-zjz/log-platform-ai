/**
 * @file    通用工具
 * @since   2026-03-22
 * @version 1.0.0
 * @author  XCF
 */

// 引入深克隆数组方法
import { cloneDeep } from 'lodash';

/**
 * 全等于(===)
 * @param val1 值1
 * @param val2 值2
 * @returns boolean - 比较结果
 */
export const equals = (val1: _, val2: _): boolean => val1 === val2;

/**
 * 不全等于(!==)
 * @param val1 值1
 * @param val2 值2
 * @returns boolean - 比较结果
 */
export const noEquals = (val1: _, val2: _): boolean => !equals(val1, val2);

/**
 * 获取值类型
 * @param val 值
 * @returns string - 值类型
 */
export const getTypeOf = (val: _): string => typeof val;

/**
 * 判断是否为未定义值
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isUndefined = (val: _): boolean => equals(undefined, val);

/**
 * 判断是否为未定义值
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isNotUndefined = (val: _): boolean => !isUndefined(val);

/**
 * 判断是否为无效值
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isNull = (val: _): boolean => equals(null, val);

/**
 * 判断是否为数字
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isNumber = (val: _): boolean => equals('number', getTypeOf(val));

/**
 * 判断值是否为字符串
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isString = (val: _): boolean => equals('string', getTypeOf(val));

/**
 * 判断值是否为空串
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isBlank = (val: _): boolean => isString(val) ? equals(0, asString(val).length) : true;

/**
 * 判断是否不为空串
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isNotBlank = (val: _): boolean => !isBlank(val);

/**
 * 判断数组是否包含某值
 * @param array 数组
 * @param value 值
 * @returns boolean - 结果
 */
export const isContains = <T> (array: Array<T>, value: T): boolean => array.includes(value);

/**
 * 判断是否为基本类型
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isFundament = (val: _): boolean => isContains(
  ['null', 'undefined', 'boolean', 'number', 'bigint', 'string', 'symbol'],
  getTypeOf(val)
);

/**
 * 判断是否为数组
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isArray = (val: _): boolean => Array.isArray(val);

/**
 * 判断是否为空数组或对象
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isEmpty = (val: Array<_> | Indexable): boolean =>
  isNotObject(val) || equals(0, (isArray(val) ? asArray(val) : Object.keys(val)).length);

/**
 * 判断是否不为空数组或对象
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isNotEmpty = (val: Array<_> | Indexable): boolean => !isEmpty(val);

/**
 * 判断是否为对象(以及数组)型
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isObject = (val: _): boolean => equals('object', getTypeOf(val));

/**
 * 判断是否不为对象(以及数组)型
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isNotObject = (val: _): boolean => !isObject(val);

/**
 * 判断是否为函数型
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isFunction = (val: _): boolean => val instanceof Function;

/**
 * 判断是否为日期型
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isDate = (val: _): boolean => val instanceof Date;

/**
 * 判断是否无意义值
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isNoValue = (val: _): boolean => isNull(val) || isUndefined(val) ||
  (isString(val) && isBlank(asString(val))) || (isObject(val) && isEmpty(val));

/**
 * 判断是否有意义值
 * @param val 待判断值
 * @returns boolean - 结果
 */
export const isValidValue = (val: _): boolean => !isNoValue(val);

/**
 * 断言值为数字型
 * @param val 待断言值
 * @returns number - 数字值
 */
export const asNumber = (val: _): number => asType(val);

/**
 * 断言值为字符串型
 * @param val 待断言值
 * @returns string - 字符串
 */
export const asString = (val: _): string => asType(val);

/**
 * 断言值为任意类型
 * @param val 待断言值
 * @returns _ - 任意型值
 */
export const asAny = <O = _> (val: O): _ => asType(val);

/**
 * 断言值为未知类型
 * @param val 待断言值
 * @returns unknown - 未知型值
 */
export const asUnknown = <O = _> (val: O): unknown => val as unknown;

/**
 * 断言值为函数型
 * @param val 待断言值
 * @returns Function - 函数型值
 */
export const asFunction = (val: _): Function => asType<_, Function>(val);

/**
 * 断言值存在
 * @param val 值
 * @returns T - 非空值
 */
export const asExist = <T> (val: MaybeEmpty<T>): T => val!;

/**
 * 断言为指定类型
 * @param val 待断言值
 * @returns T - 指定类型值
 */
export const asType = <O = _, T = _> (val: O): T => asUnknown(val) as T;

/**
 * 断言值为数组型
 * @param val 待断言值
 * @returns Array<_> - 数组型值
 */
export const asArray = <T = _> (val: _): Array<T> => asType(val);

/**
 * 断言值为日期型
 * @param val 待断言值
 * @returns Date - 日期型值
 */
export const asDate = (val: _): Date => asType(val);

/**
 * 反射判断某对象上是否存在某属性
 * @param object 对象
 * @param key    属性键值
 * @returns boolean - 结果
 */
export const hasProperty = (object: _, key: string): boolean => Reflect.has(object, key);

/**
 * 反射判断存在并获取指定对象的指定属性
 * @param object 对象
 * @param key    属性键值
 * @returns _ - 属性值
 */
export const getIfHasProperty = (object: _, key: string): _ => hasProperty(object, key) ? Reflect.get(object, key) : undefined;

/**
 * 将值转换为字符串型
 * @param val 值
 * @returns string - 字符串
 */
export const toString = (val: _): string => {
  if (isFundament(val)) {
    return String(val);
  }
  const toString: MaybeUndefined<Function> = getIfHasProperty(val, 'toString');
  return isUndefined(toString) ? String(val) : asFunction(toString).apply(val);
};

/**
 * 将值转换为布尔型
 * @param val 值
 * @returns boolean - 布尔值
 */
export const toBoolean = (val: _): boolean => val != 'false' && Boolean(val);

/**
 * 将值转换为日期型
 * @param val 值
 * @returns Date - 日期型值
 */
export const toDate = (val?: DateLike): Date => {
  if (isDate(val)) {
    return asDate(val);
  }
  return isValidValue(val) ? new Date(asType(val)) : new Date();
};


/**
 * `ESCAPE`键按下监听器
 * @param event   键盘事件
 * @param handler 事件处理器
 */
export const escapeListener = (event: KeyboardEvent, handler: () => void): void => {
  if (equals('Escape', event.key)) {
    handler();
  }
};

/**
 * 使用lodash库方法进行数组或对象深克隆<br />
 * <b color="red">注意！若数组嵌套层级过多，可能会造成堆栈溢出或其他性能问题</b>
 * @param val 待拷贝数组或对象
 * @returns T - 深克隆结果
 */
export const deepClone = <T extends Union<Array<_>, Indexable>> (val: T): T => cloneDeep(val);

/**
 * 向数组中<div color="red"><b>原地</b></div>添加元素并返回
 * @param array            数组
 * @param indexOrFindIndex 移除开始下标或下标查找函数
 * @param addition         追加的数据
 * @returns Array<T> - 添加元素后的数组
 */
export const addToArray = <T = _> (array: Array<T>, indexOrFindIndex: number | ((value?: T, index?: number, arr?: Array<T>) => boolean),
                                   ...addition: Array<T>): Array<T> => {
  isNumber(indexOrFindIndex) ?
    array.splice(asNumber(indexOrFindIndex), 0, ...addition) :
    array.splice(array.findIndex(asType(indexOrFindIndex)), 0, ...addition);
  return array;
};

/**
 * 从数组中<div color="red"><b>原地</b></div>移除元素并返回
 * @param array            数组
 * @param indexOrFindIndex 移除开始下标或下标查找函数
 * @param removeNumber     移除元素数量，默认：1
 * @returns Array<T> - 删除元素后的数组
 */
export const removeFromArray = <T = _> (array: Array<T>, indexOrFindIndex: number | ((value?: T, index?: number, arr?: Array<T>) => boolean),
                                        removeNumber: number = 1): Array<T> => {
  isNumber(indexOrFindIndex) ?
    array.splice(asNumber(indexOrFindIndex), removeNumber) :
    array.splice(array.findIndex(asType(indexOrFindIndex)), removeNumber);
  return array;
};

/**
 * 键值对类
 */
export class Entry<K extends string = string, V = _> {

  /**
   * 键
   */
  public readonly key: K;

  /**
   * 值
   */
  public readonly value: V;

  /**
   * 全参构造器
   * @param key   键
   * @param value 值
   * @private
   */
  private constructor(key: K, value: V) {
    this.key = key;
    this.value = value;
  };

  /**
   * 静态全参构造器
   * @param key   键
   * @param value 值
   * @returns Entry - 键值对对象
   * @readonly
   */
  public static readonly of = <K extends string = string, V = _>(key: K, value: V): Entry<K, V> => new this(key, value);

  /**
   * 静态单参数构造器
   * @param object 对象
   * @returns Entru - 键值对对象
   * @readonly
   */
  public static readonly from = <K extends string = string, V = _>(object: Indexable): Entry<K, V> => {
    const keys: Array<K> = asType(Object.keys(object));
    if (isEmpty(keys) || keys.length > 1) {
      throw new Error('传入了参数数量不符合的对象');
    }
    const values: Array<V> = Object.values(object);
    return this.of(keys[0], values[0]);
  };
}

/**
 * 对象映射
 */
export class ObjectMap<K = string, V = _> {

  /**
   * 持有映射
   * @private
   */
  private map: Map<K, V> = new Map();

  /**
   * 无参构造器
   * @private
   */
  private constructor() {};

  /**
   * 静态无参构造器
   */
  public static readonly create = <K = string, V = _> (): ObjectMap<K, V> => new ObjectMap<K, V>();

  /**
   * 静态单参构造器
   * @param origin 原始对象
   */
  public static readonly of = <V = _> (origin: Indexable): ObjectMap<string, V> => {
    const objectMap: ObjectMap<string, V> = new ObjectMap<string, V>();
    Object.entries(origin).forEach(([key, value]) => objectMap.set(key, value));
    return objectMap;
  };

  /**
   * 设置值
   * @param key   键
   * @param value 值
   * @returns this - 链式返回
   */
  public readonly set = (key: K, value: V): this => {
    this.map.set(key, value);
    return this;
  };

  /**
   * 获取值
   * @param key 键
   * @returns MaybeUndefined<V> - 值(可能为空)
   */
  public readonly get = (key: K): MaybeUndefined<V> => this.map.get(key);

  /**
   * 获取全部键
   * @returns Array<K> - 键数组
   */
  public readonly keys = (): Array<K> => Array.of(...this.map.keys());

  /**
   * 获取全部值
   * @returns Array<V> - 值数组
   */
  public readonly values = (): Array<V> => Array.of(...this.map.values());

  /**
   * 获取全部键 | 值映射元组
   * @return Array<[K, V]> - 键 | 值映射元组数组
   */
  public readonly entries = (): Array<[K, V]> => Array.of(...this.map.entries());

  /**
   * 判断键是否存在
   * @param key 键
   * @return boolean - 结果
   */
  public readonly has = (key: K): boolean => this.map.has(key);

  /**
   * 删除值
   * @param key 键
   * @returns this - 链式返回
   */
  public readonly delete = (key: K): this => {
    this.map.delete(key);
    return this;
  };

  /**
   * 追加全部值
   * @param map 映射 | 对象映射
   * @returns this - 链式返回
   */
  public readonly addAll = (map: Union<Map<K, V>, ObjectMap<K, V>>): ObjectMap<K, V> => asType(
    ObjectMap.of(map instanceof Map ?
      { ...this.toIndexable(), ...Object.fromEntries(map.entries()) } :
      { ...this.toIndexable(), ...map.toIndexable() }
    )
  );

  /**
   * 删除多个值
   * @param keys 键数组
   * @returns this - 链式返回
   */
  public readonly deleteAll = (...keys: Array<K>): this => {
    keys.forEach(this.delete);
    return this;
  };

  /**
   * 清空映射
   * @returns this - 链式返回
   */
  public readonly clear = (): this => {
    this.map.clear();
    return this;
  };

  /**
   * 转换为可索引对象
   * @returns Indexable - 可索引对象
   */
  public readonly toIndexable = (): Indexable => {
    const result: Indexable = {};
    this.map.forEach((value: V, key: K) => result[toString(key)] = value);
    return result;
  };
}
