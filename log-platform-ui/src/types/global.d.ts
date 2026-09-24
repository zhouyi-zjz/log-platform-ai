/**
 * @file    全局类型定义
 * @since   2026-03-22
 * @version 1.0.0
 * @author  XCF
 */
// 引入Alova方法类型及相关泛型类型
import type { Method, AlovaGenerics, RespondedAlovaGenerics } from 'alova';

/**
 * 定义全局类型
 */
/* prettier-ignore */
declare global {

  /**
   * 可索引类型
   */
  declare interface Indexable {
    [k: string]: _;
  }

  /**
   * 选择器选项
   */
  declare interface SelectOption {
    label: string;
    value: _;
    disabled?: boolean;
  }

  /**
   * 级联选择器选项
   */
  declare interface CascadeOption extends SelectOption {
    children?: Array<CascadeOption>
  }

  /**
   * 树形控件选项
   */
  declare interface TreeOption {
    label?: string,
    children?: Array<TreeOption>
  }

  /**
   * 基础响应
   */
  declare interface BaseResult {
    code: number;
    msg?: string;
  }

  /**
   * 基础请求方法响应
   */
  declare type BaseMethodResult<R> = Method<RespondedAlovaGenerics<AlovaGenerics, R, _>>;

  /**
   * 消息响应
   * @override
   */
  declare interface MsgResult extends BaseResult {
    msg: string;
  }

  /**
   * 请求方法消息响应
   */
  declare type MethodMsgResult = BaseMethodResult<MsgResult>;

  /**
   * 数据响应
   */
  declare interface DataResult<T extends Indexable = _> extends BaseResult {
    data: T;
  }

  /**
   * 请求方法数据响应
   */
  declare type MethodDataResult<T extends Indexable = _> = BaseMethodResult<DataResult<T>>;

  /**
   * 列表响应
   */
  declare interface ListResult<T extends Indexable = _> extends BaseResult {
    data: Array<T>;
  }

  /**
   * 请求方法列表响应
   */
  declare type MethodListResult<T extends Indexable = _> = BaseMethodResult<ListResult<T>>;

  /**
   * 分页响应
   */
  declare interface PageResult<T extends Indexable = _> extends BaseResult {
    rows: Array<T>;
    total: number;
  }

  /**
   * 请求方法分页响应
   */
  declare type MethodPageResult<T extends Indexable = _> = BaseMethodResult<PageResult<T>>;

  /**
   * 类日期类型：日期对象 | 日期字符串 | 日期毫秒数
   */
  declare type DateLike = Date | string | number;

  /**
   * 可能为undefined类型
   */
  declare type MaybeUndefined<T> = T | undefined;

  /**
   * 可能为null类型
   */
  declare type MaybeNull<T> = T | null;

  /**
   * 可能为空类型
   */
  declare type MaybeEmpty<T> = MaybeUndefined<MaybeNull<T>>;

  /**
   * 可能为数组类型
   */
  declare type MaybeArray<T> = T | Array<T>;

  /**
   * 定长数组
   */
  declare type FixedArray<T, N extends number> = [T, ...Array<T>] & { length: N };

  /**
   * 全局样式类型，默认 | 成功 | 警告 | 异常 | 消息 | 完成 | 无
   */
  declare type ClassType = 'default' | 'success' | 'warning' | 'error' | 'info';

  /**
   * 权限检查类型
   */
  declare interface RoleCheck<T extends Indexable = Indexable> {
    // 角色字符
    roles?: Array<string>;
    // 权限字符
    permis?: Array<string>;
    // 自定义方法
    custom?: (row: T) => boolean;
  }

  /**
   * 对象键类型
   */
  declare type ObjectKeys<T extends Indexable> = {
    [K in keyof T]: K extends string ? `${K & string}` : string
  }[keyof T];

  /**
   * 对象值类型
   */
  declare type ObjectValues<T extends Indexable> = T[ObjectKeys<T>];

  /**
   * 上传进度类型
   */
  declare interface Uploading {
    // 上传完成字节数
    loaded: number;
    // 总字节数
    total: number;
  }

  /**
   * 单选框选项类型
   */
  declare interface RadioOption extends SelectOption {
    // 描述
    desc?: string;
  }

  /**
   * css选择器类型
   */
  declare type CssSelector = `#${string}` | `.${string}` | ObjectKeys<HTMLElementTagNameMap>;

  /**
   * dom选择器类型
   */
  declare type DomSelector = CssSelector | HTMLElement;

  /**
   * 简写any
   */
  declare type _ = any;

  /**
   * 字典值类型
   */
  declare type DictValueType = `${number}`;

  /**
   * 交叉类型(2-10位)
   */
  declare type Cross<T1, T2, T3 = void, T4 = void, T5 = void, T6 = void, T7 = void, T8 = void, T9 = void, T10 = void> =
    T1 & T2
    & (T3 extends void ? {} : T3)
    & (T4 extends void ? {} : T4)
    & (T5 extends void ? {} : T5)
    & (T6 extends void ? {} : T6)
    & (T7 extends void ? {} : T7)
    & (T8 extends void ? {} : T8)
    & (T9 extends void ? {} : T9)
    & (T10 extends void ? {} : T10);

  /**
   * 联合类型(2-10位)
   */
  declare type Union<T1, T2, T3 = void, T4 = void, T5 = void, T6 = void, T7 = void, T8 = void, T9 = void, T10 = void> =
    T1 | T2
    | (T3 extends void ? never : T3)
    | (T4 extends void ? never : T4)
    | (T5 extends void ? never : T5)
    | (T6 extends void ? never : T6)
    | (T7 extends void ? never : T7)
    | (T8 extends void ? never : T8)
    | (T9 extends void ? never : T9)
    | (T10 extends void ? never : T10);

  /**
   * 验证器类型
   */
  declare type Validator = (rule: _, value: _, callback: _) => _;

  /**
   * ID类型
   */
  declare type IdLike = Union<string, number>;

  /**
   * 基础分页查询参数
   */
  declare interface PageQuery {
    // 页码
    pageNum: number;
    // 一页数据总数
    pageSize: number;
  }

  /**
   * 分页查询实体类型
   */
  declare type SelectPageQuery<T> = Cross<T, PageQuery>;

  /**
   * 代码拷贝目标数据类型
   */
  declare interface CopyCodeEventTarget {
    // 代码块唯一ID
    uid: string;
  }

  /**
   * 拓展window事件类型
   */
  interface WindowEventMap {

    /**
     * 添加自定义代码拷贝事件
     */
    'copy-code': CustomEvent<CopyCodeEventTarget>;
  }
}

export {};
