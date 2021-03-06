package ru.hh.jclient.errors.impl.check;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.ws.rs.WebApplicationException;
import ru.hh.jclient.common.ResultWithStatus;
import ru.hh.jclient.errors.impl.PredicateWithStatus;

/**
 * Contains useful methods to handle error outcome of {@link CompletableFuture} with different jclient-common wrappers.
 */
public class ApplyResultOperation<T> extends AbstractOperation<T, ApplyResultOperation<T>> {

  // constructors

  public ApplyResultOperation(
      ResultWithStatus<T> wrapper,
      Optional<Integer> errorStatusCode,
      Optional<List<Integer>> proxiedStatusCodes,
      Optional<Function<Integer, Integer>> statusCodesConverter,
      Supplier<String> errorMessage,
      List<PredicateWithStatus<T>> predicates) {
    super(wrapper, errorStatusCode, proxiedStatusCodes, statusCodesConverter, errorMessage, predicates);
  }

  public ApplyResultOperation(
      ResultWithStatus<T> wrapper,
      Optional<Integer> errorStatusCode,
      Optional<List<Integer>> proxiedStatusCodes,
      Optional<Function<Integer, Integer>> statusCodesConverter,
      Supplier<String> errorMessage,
      List<PredicateWithStatus<T>> predicates,
      Optional<T> defaultValue) {
    super(wrapper, errorStatusCode, proxiedStatusCodes, statusCodesConverter, errorMessage, predicates, defaultValue);
  }

  // terminal operations

  /**
   * <p>
   * Returns result or throws {@link WebApplicationException} with provided status code on any error including:
   * <ul>
   * <li>{@link ResultWithStatus#isSuccess()} is false</li>
   * <li>predicate provided with {@link AbstractOperationSelector#failIf(java.util.function.Predicate)} says ResultWithStatus contains incorrect value
   * </li>
   * <li>{@link ResultWithStatus#get()} contains {@link Optional#empty()}</li>
   * </ul>
   * </p>
   * <p>
   * If default value is specified, it will be returned instead of exception.
   * </p>
   *
   * @throws WebApplicationException
   *           with provided status code and message in case of error (if default value is not specified)
   * @return unwrapped non null result or default value (if specified) in case of error
   */
  public T onAnyError() {
    return checkForAnyError().get();
  }

  /**
   * <p>
   * Returns result or throws {@link WebApplicationException} with provided status code if {@link ResultWithStatus#get()} contains
   * {@link Optional#empty()}.
   * </p>
   * <p>
   * If default value is specified, it will be returned instead of exception.
   * </p>
   *
   * @throws WebApplicationException
   *           with provided status code and message in case of error (if default value is not specified)
   * @return unwrapped non null result or default value (if specified) in case of error
   */
  public T onEmpty() {
    return checkForEmpty();
  }

  /**
   * <p>
   * Returns result or throws {@link WebApplicationException} with provided status code on status code error - if {@link ResultWithStatus#isSuccess()}
   * is false.
   * </p>
   * <p>
   * If default value is specified, it will be returned instead of exception.
   * </p>
   *
   * @throws WebApplicationException
   *           with provided status code and message in case of error (if default value is not specified)
   * @return result or default value (if specified) in case of error
   */
  public Optional<T> onStatusCodeError() {
    return checkForStatusCodeError();
  }

  /**
   * <p>
   * Returns result or throws {@link WebApplicationException} with provided status code if predicate specified with
   * {@link AbstractOperationSelector#failIf(java.util.function.Predicate)} returns 'true'.
   * </p>
   * <p>
   * If default value is specified, it will be returned instead of exception.
   * </p>
   *
   * @throws WebApplicationException
   *           with provided status code and message in case of error (if default value is not specified)
   * @return result or default value (if specified) in case of error
   */
  public Optional<T> onPredicate() {
    return checkForPredicates(wrapper.get());
  }

}
